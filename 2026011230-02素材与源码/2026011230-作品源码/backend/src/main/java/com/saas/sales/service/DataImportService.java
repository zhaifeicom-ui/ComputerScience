package com.saas.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saas.sales.entity.DataImportJob;
import com.saas.sales.entity.ImportJobStatus;
import com.saas.sales.entity.Product;
import com.saas.sales.entity.SalesData;
import com.saas.sales.mapper.DataImportJobMapper;
import com.saas.sales.mapper.ProductMapper;
import com.saas.sales.mapper.SalesDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.saas.sales.config.TenantContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DataImportService {

    @Autowired
    private DataImportJobMapper dataImportJobMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SalesDataMapper salesDataMapper;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private com.saas.sales.mapper.InventoryMapper inventoryMapper;

    @Value("${app.upload.error-report-dir:./error-reports}")
    private String errorReportDir;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT
            .withFirstRecordAsHeader()
            .withIgnoreHeaderCase()
            .withTrim();

    @Transactional
    public Long createImportJob(String fileName, Long tenantId) {
        DataImportJob job = new DataImportJob();
        job.setTenantId(tenantId);
        job.setFileName(fileName);
        job.setStatus(ImportJobStatus.PENDING.name());
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setCreatedAt(LocalDateTime.now());

        dataImportJobMapper.insert(job);
        return job.getId();
    }


    public void processImportData(Long jobId, byte[] fileBytes, String fileName, Long tenantId) {
        // 由于是异步方法，在新线程中执行，需要重新设置租户上下文
        TenantContextHolder.setTenantId(tenantId);
        try {
            DataImportJob job = dataImportJobMapper.selectById(jobId);
            if (job == null) {
                log.error("任务不存在: {}", jobId);
                return;
            }

            // 更新状态为处理中
            job.setStatus(ImportJobStatus.PROCESSING.name());
            job.setErrorReportUrl(null);
            dataImportJobMapper.updateById(job);

            int successCount = 0;
            int failureCount = 0;
            List<String> errorLines = new ArrayList<>();
            List<SalesData> batch = new ArrayList<>();
            long batchStartTime = System.currentTimeMillis();

            // 先将所有产品缓存到内存，避免每行都去查数据库，提升性能
            java.util.List<Product> products = productMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                    .eq(Product::getTenantId, tenantId)
            );
            java.util.Map<Long, Product> productMap = new java.util.HashMap<>();
            for(Product p : products) {
                productMap.put(p.getId(), p);
            }

            // 将所有库存缓存到内存
            java.util.List<com.saas.sales.entity.Inventory> inventories = inventoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.saas.sales.entity.Inventory>()
                    .eq(com.saas.sales.entity.Inventory::getTenantId, tenantId)
            );
            java.util.Map<Long, com.saas.sales.entity.Inventory> inventoryMap = new java.util.HashMap<>();
            for(com.saas.sales.entity.Inventory inv : inventories) {
                inventoryMap.put(inv.getProductId(), inv);
            }

            try {
                if (fileName.toLowerCase().endsWith(".csv")) {
                    String csvContent = new String(fileBytes, StandardCharsets.UTF_8);
                    try (BufferedReader reader = new BufferedReader(new java.io.StringReader(csvContent));
                         CSVParser csvParser = new CSVParser(reader, CSV_FORMAT)) {
                        int lineNumber = 1; // 包括标题行
                        for (CSVRecord record : csvParser) {
                            lineNumber++;
                            try {
                                SalesData salesData = parseSalesDataRecord(record.toMap(), tenantId, productMap, inventoryMap);
                                if (salesData != null) {
                                    batch.add(salesData);
                                    successCount++;
                                } else {
                                    failureCount++;
                                    errorLines.add("第" + lineNumber + "行: 记录无效");
                                }
                            } catch (Exception e) {
                                failureCount++;
                                errorLines.add("第" + lineNumber + "行: " + e.getMessage());
                            }
                            
                            if (batch.size() >= 1000) {
                                saveBatchSalesData(batch);
                                batch.clear();
                            }
                        }
                    }
                } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                    try (InputStream is = new ByteArrayInputStream(fileBytes);
                         Workbook workbook = new XSSFWorkbook(is)) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row headerRow = sheet.getRow(0);
                        if (headerRow == null) {
                            throw new RuntimeException("Excel文件为空或没有表头");
                        }
                        
                        // 解析表头
                        List<String> headers = new ArrayList<>();
                        for (Cell cell : headerRow) {
                            headers.add(getCellValueAsString(cell));
                        }
                        
                        int lineNumber = 1;
                        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                            Row row = sheet.getRow(i);
                            if (row == null) continue;
                            lineNumber++;
                            
                            Map<String, String> recordMap = new HashMap<>();
                            boolean isEmptyRow = true;
                            for (int j = 0; j < headers.size(); j++) {
                                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                                String cellValue = getCellValueAsString(cell);
                                if (!cellValue.trim().isEmpty()) {
                                    isEmptyRow = false;
                                }
                                recordMap.put(headers.get(j), cellValue);
                            }
                            
                            if (isEmptyRow) continue;
                            
                            try {
                                SalesData salesData = parseSalesDataRecord(recordMap, tenantId, productMap, inventoryMap);
                                if (salesData != null) {
                                    batch.add(salesData);
                                    successCount++;
                                } else {
                                    failureCount++;
                                    errorLines.add("第" + lineNumber + "行: 记录无效");
                                }
                            } catch (Exception e) {
                                failureCount++;
                                errorLines.add("第" + lineNumber + "行: " + e.getMessage());
                            }
                            
                            if (batch.size() >= 1000) {
                                saveBatchSalesData(batch);
                                batch.clear();
                            }
                        }
                    }
                }

                // 插入剩余记录
                if (!batch.isEmpty()) {
                    saveBatchSalesData(batch);
                }

                // 生成错误报告
                String errorReportUrl = null;
                if (!errorLines.isEmpty()) {
                    errorReportUrl = generateErrorReport(jobId, errorLines);
                }

                // 更新任务状态
                job.setStatus(ImportJobStatus.COMPLETED.name());
                job.setSuccessCount(successCount);
                job.setFailureCount(failureCount);
                job.setErrorReportUrl(errorReportUrl);
                job.setFinishedAt(LocalDateTime.now());
                
                log.info("任务[{}] 导入完成: 成功 {}, 失败 {}", jobId, successCount, failureCount);

            } catch (Exception e) {
                log.error("处理文件失败: {}", e.getMessage(), e);
                job.setStatus(ImportJobStatus.FAILED.name());
                job.setErrorReportUrl(generateErrorReport(jobId, List.of("处理失败: " + e.getMessage())));
                failureCount++;
            } finally {
                job.setSuccessCount(successCount);
                job.setFailureCount(failureCount);
                dataImportJobMapper.updateById(job);
            }
        } finally {
            // 清理租户上下文，防止内存泄漏
            TenantContextHolder.clear();
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER);
                }
                // 防止数字变成科学计数法或带小数点的整数
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
            default:
                return "";
        }
    }

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private SalesData parseSalesDataRecord(Map<String, String> recordMap, Long tenantId, java.util.Map<Long, Product> productMap, java.util.Map<Long, com.saas.sales.entity.Inventory> inventoryMap) {
        try {
            // 泛用性列名匹配
            String dateStr = getValueWithFallback(recordMap, "date", "ds", "日期", "\u65e5\u671f", "\ufeff日期", "\ufeffdate");
            if (dateStr == null || dateStr.trim().isEmpty()) {
                throw new RuntimeException("缺少日期列(date/ds/日期)");
            }
            dateStr = dateStr.trim();
            
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                // 尝试其他格式
                try {
                    date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                } catch (DateTimeParseException ex) {
                    throw new RuntimeException("日期格式错误，支持yyyy-MM-dd或yyyy/MM/dd");
                }
            }

            String productIdStr = getValueWithFallback(recordMap, "product_id", "productId", "product", "商品ID", "商品编号");
            Long productId = null;
            Product product = null;
            
            // 如果提供了商品 ID，则按 ID 查找
            if (productIdStr != null && !productIdStr.trim().isEmpty()) {
                try {
                    productId = Long.parseLong(productIdStr);
                    product = productMap.get(productId);
                } catch (NumberFormatException e) {
                    // 忽略，尝试按名称查找
                }
            }
            
            // 如果没有提供商品 ID 或者按 ID 没找到，尝试按商品名称查找
            if (product == null) {
                String productNameStr = getValueWithFallback(recordMap, "product_name", "productName", "name", "商品名称", "商品名");
                if (productNameStr != null && !productNameStr.trim().isEmpty()) {
                    // 遍历 productMap 寻找匹配的商品名称
                    for (Product p : productMap.values()) {
                        if (productNameStr.equals(p.getName())) {
                            product = p;
                            productId = p.getId();
                            break;
                        }
                    }
                }
            }

            if (product == null) {
                throw new RuntimeException("产品不存在或不属于当前租户 (请检查商品ID或商品名称是否正确)");
            }

            String salesStr = getValueWithFallback(recordMap, "sales", "y", "销量", "数量");
            if (salesStr == null) throw new RuntimeException("缺少销量列");
            // 支持带有小数点的销量数据如"150.0" 并且防止解析空字符串异常
            Integer sales = 0;
            if (!salesStr.trim().isEmpty()) {
                 sales = (int) Double.parseDouble(salesStr); 
            }
            if (sales < 0) {
                throw new RuntimeException("销量不能为负数: " + sales);
            }

            // 校验销量是否大于库存
            com.saas.sales.entity.Inventory inventory = inventoryMap.get(productId);
            if (inventory == null) {
                throw new RuntimeException("该产品暂无库存记录，无法导入销售数据");
            }
            if (sales > inventory.getStock()) {
                throw new RuntimeException("导入的销售数量(" + sales + ")大于当前商品库存(" + inventory.getStock() + ")");
            }
            
            // 扣减内存中的库存，防止同一文件内多次导入导致总销量超出库存
            inventory.setStock(inventory.getStock() - sales);

            String priceStr = getValueWithFallback(recordMap, "price", "价格", "单价");
            Double price = null;
            if (priceStr != null && !priceStr.trim().isEmpty()) {
                try {
                    price = Double.parseDouble(priceStr);
                    if (price <= 0) {
                        throw new RuntimeException("价格必须大于0: " + price);
                    }
                } catch (NumberFormatException e) {
                    // 如果价格无法解析，忽略或者记录警告，不直接抛出异常导致行失败
                    log.warn("无法解析价格: {}", priceStr);
                }
            }

            SalesData salesData = new SalesData();
            salesData.setTenantId(tenantId);
            salesData.setProductId(productId);
            salesData.setDate(date);
            salesData.setSales(sales);
            salesData.setPrice(price != null ? BigDecimal.valueOf(price) : null);
            salesData.setCreatedAt(LocalDateTime.now());

            return salesData;
        } catch (NumberFormatException e) {
            throw new RuntimeException("数值格式错误: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("解析错误: " + e.getMessage());
        }
    }
    
    private String getValueWithFallback(java.util.Map<String, String> recordMap, String... keys) {
        for (String key : keys) {
            String value = recordMap.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        // 如果上面没找到，尝试忽略大小写和 BOM 头进行匹配
        for (java.util.Map.Entry<String, String> entry : recordMap.entrySet()) {
            String mapKey = entry.getKey();
            if (mapKey == null) continue;
            mapKey = mapKey.trim().toLowerCase();
            if (mapKey.startsWith("\ufeff")) {
                mapKey = mapKey.substring(1);
            }
            for (String key : keys) {
                if (mapKey.equals(key.toLowerCase())) {
                    String value = entry.getValue();
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
    
    private boolean isStandardColumn(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase().trim();
        // 移除可能的BOM头
        if (lowerKey.startsWith("\ufeff")) {
            lowerKey = lowerKey.substring(1);
        }
        return lowerKey.equals("date") || lowerKey.equals("ds") || lowerKey.equals("日期") ||
               lowerKey.equals("product_id") || lowerKey.equals("productid") || lowerKey.equals("product") || lowerKey.equals("商品id") || lowerKey.equals("商品编号") ||
               lowerKey.equals("product_name") || lowerKey.equals("productname") || lowerKey.equals("name") || lowerKey.equals("商品名称") || lowerKey.equals("商品名") ||
               lowerKey.equals("sales") || lowerKey.equals("y") || lowerKey.equals("销量") || lowerKey.equals("数量") ||
               lowerKey.equals("price") || lowerKey.equals("价格") || lowerKey.equals("单价") ||
               lowerKey.equals("tenant_id") || lowerKey.equals("tenantid");
    }

    private void saveBatchSalesData(List<SalesData> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            // 使用自定义的批量插入方法
            salesDataMapper.insertBatchSomeColumn(batch);
        } catch (Exception e) {
            // 回退到逐条插入
            log.warn("批量插入失败，回退到逐条插入: {}", e.getMessage());
            for (SalesData salesData : batch) {
                try {
                    salesDataMapper.insert(salesData);
                } catch (Exception ex) {
                    log.error("逐条插入失败: {}", ex.getMessage());
                }
            }
        }
        
        // 批量更新库存
        for (SalesData salesData : batch) {
            try {
                LocalDateTime createTime = salesData.getCreatedAt() != null ? salesData.getCreatedAt() : LocalDateTime.now();
                inventoryService.updateInventoryFromSales(salesData.getProductId(), createTime, salesData.getSales());
            } catch (Exception e) {
                log.error("更新产品库存失败, productId: {}, error: {}", salesData.getProductId(), e.getMessage());
            }
        }
    }

    private String generateErrorReport(Long jobId, List<String> errorLines) {
        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get(errorReportDir);
            if (!java.nio.file.Files.exists(dirPath)) {
                java.nio.file.Files.createDirectories(dirPath);
            }

            String fileName = "error_report_" + jobId + "_" + System.currentTimeMillis() + ".txt";
            java.nio.file.Path filePath = dirPath.resolve(fileName);

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                for (String line : errorLines) {
                    writer.write(line + "\n");
                }
            }

            return "/error-reports/" + fileName;
        } catch (IOException e) {
            log.error("生成错误报告失败", e);
            return null;
        }
    }

    public DataImportJob getJobById(Long jobId) {
        return dataImportJobMapper.selectById(jobId);
    }

    public List<DataImportJob> getJobsByTenant(Long tenantId) {
        LambdaQueryWrapper<DataImportJob> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(DataImportJob::getCreatedAt);
        return dataImportJobMapper.selectList(queryWrapper);
    }

    @Transactional
    public void deleteJob(Long jobId, Long tenantId) {
        DataImportJob job = dataImportJobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("任务不存在或无权访问");
        }
        dataImportJobMapper.deleteById(jobId);
        log.info("删除导入任务: {}, 租户: {}", jobId, tenantId);
    }
}