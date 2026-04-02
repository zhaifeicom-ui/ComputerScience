package com.saas.sales.service;

import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.dto.SalesTrendResponse;
import com.saas.sales.dto.TopProductResponse;
import com.saas.sales.entity.Product;
import com.saas.sales.entity.SalesData;
import com.saas.sales.mapper.ProductMapper;
import com.saas.sales.mapper.SalesDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnalysisService {

    @Autowired
    private SalesDataMapper salesDataMapper;

    @Autowired
    private ProductMapper productMapper;

    public List<SalesTrendResponse> getSalesTrend(String period, LocalDate startDate, LocalDate endDate, Integer limit) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 复制到final变量以便在lambda中使用
        final String finalPeriod = period;
        final LocalDate finalStartDate;
        final LocalDate finalEndDate;
        
        // 如果未提供日期范围，默认查询最近30天
        if (startDate == null) {
            finalEndDate = LocalDate.now();
            finalStartDate = finalEndDate.minusDays(30);
        } else {
            finalStartDate = startDate;
            finalEndDate = endDate != null ? endDate : LocalDate.now();
        }

        // 查询指定日期范围内的销售数据
        List<SalesData> salesDataList = salesDataMapper.selectSalesDataByDateRange(
                finalStartDate, finalEndDate);

        if (salesDataList.isEmpty()) {
            return Collections.emptyList();
        }

        // 根据周期聚合数据
        Map<LocalDate, List<SalesData>> groupedData;
        
        switch (finalPeriod.toLowerCase()) {
            case "daily":
                groupedData = salesDataList.stream()
                        .collect(Collectors.groupingBy(SalesData::getDate));
                break;
            case "weekly":
                groupedData = salesDataList.stream()
                        .collect(Collectors.groupingBy(data -> {
                            // 获取该日期所在周的周一
                            return data.getDate().with(java.time.DayOfWeek.MONDAY);
                        }));
                break;
            case "monthly":
                groupedData = salesDataList.stream()
                        .collect(Collectors.groupingBy(data -> {
                            // 使用该月的第一天
                            return LocalDate.of(data.getDate().getYear(), data.getDate().getMonth(), 1);
                        }));
                break;
            default:
                throw new RuntimeException("不支持的周期类型: " + finalPeriod);
        }

        // 转换为响应对象
        List<SalesTrendResponse> trends = groupedData.entrySet().stream()
                .map(entry -> {
                    LocalDate groupDate = entry.getKey();
                    List<SalesData> dataList = entry.getValue();
                    
                    int totalSales = dataList.stream().mapToInt(SalesData::getSales).sum();
                    BigDecimal totalRevenue = dataList.stream()
                            .map(data -> {
                                BigDecimal price = data.getPrice();
                                if (price == null) {
                                    return BigDecimal.ZERO;
                                }
                                return price.multiply(BigDecimal.valueOf(data.getSales()));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    int productCount = (int) dataList.stream()
                            .map(SalesData::getProductId)
                            .distinct()
                            .count();
                    
                    return new SalesTrendResponse(groupDate, totalSales, totalRevenue, productCount);
                })
                .sorted(Comparator.comparing(SalesTrendResponse::getDate))
                .collect(Collectors.toList());

        // 限制返回数量
        if (limit != null && limit > 0 && trends.size() > limit) {
            trends = trends.subList(trends.size() - limit, trends.size());
        }

        return trends;
    }

    public List<TopProductResponse> getTopProducts(String period, LocalDate startDate, LocalDate endDate, Integer limit) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 复制到final变量以便在lambda中使用
        final String finalPeriod = period;
        final LocalDate finalStartDate;
        final LocalDate finalEndDate;
        
        // 如果未提供日期范围，默认查询最近30天
        if (startDate == null) {
            finalEndDate = LocalDate.now();
            finalStartDate = finalEndDate.minusDays(30);
        } else {
            finalStartDate = startDate;
            finalEndDate = endDate != null ? endDate : LocalDate.now();
        }

        // 查询指定日期范围内的销售数据
        List<SalesData> salesDataList = salesDataMapper.selectSalesDataByDateRange(
                finalStartDate, finalEndDate);

        if (salesDataList.isEmpty()) {
            return Collections.emptyList();
        }

        // 按产品聚合销售数据
        Map<Long, List<SalesData>> productSalesMap = salesDataList.stream()
                .collect(Collectors.groupingBy(SalesData::getProductId));

        // 获取产品信息
        Map<Long, Product> productMap = productMapper.selectBatchIds(productSalesMap.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        // 计算每个产品的总销量和总销售额
        List<TopProductResponse> topProducts = productSalesMap.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    List<SalesData> productSales = entry.getValue();
                    
                    Product product = productMap.get(productId);
                    String productName = product != null ? product.getName() : "未知产品";
                    
                    int totalSales = productSales.stream().mapToInt(SalesData::getSales).sum();
                    BigDecimal totalRevenue = productSales.stream()
                            .map(data -> {
                                BigDecimal price = data.getPrice();
                                if (price == null) {
                                    return BigDecimal.ZERO;
                                }
                                return price.multiply(BigDecimal.valueOf(data.getSales()));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    // 计算增长率（简化版：与上一个周期比较）
                    Double growthRate = calculateGrowthRate(productId, finalPeriod, finalStartDate, finalEndDate);
                    
                    return new TopProductResponse(productId, productName, totalSales, totalRevenue, growthRate);
                })
                .sorted((p1, p2) -> p2.getTotalSales().compareTo(p1.getTotalSales())) // 按销量降序
                .collect(Collectors.toList());

        // 限制返回数量
        if (limit != null && limit > 0 && topProducts.size() > limit) {
            topProducts = topProducts.subList(0, limit);
        }

        return topProducts;
    }

    public Map<String, Object> getSalesSummary(LocalDate startDate, LocalDate endDate) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 复制到final变量以便在lambda中使用
        final LocalDate finalStartDate;
        final LocalDate finalEndDate;
        
        // 如果未提供日期范围，默认查询最近30天
        if (startDate == null) {
            finalEndDate = LocalDate.now();
            finalStartDate = finalEndDate.minusDays(30);
        } else {
            finalStartDate = startDate;
            finalEndDate = endDate != null ? endDate : LocalDate.now();
        }

        // 查询销售数据
        List<SalesData> salesDataList = salesDataMapper.selectSalesDataByDateRange(
                finalStartDate, finalEndDate);

        Map<String, Object> summary = new HashMap<>();
        
        if (salesDataList.isEmpty()) {
            summary.put("totalSales", 0);
            summary.put("totalRevenue", BigDecimal.ZERO);
            summary.put("averageDailySales", 0);
            summary.put("productCount", 0);
            summary.put("bestSellingProduct", "无");
            summary.put("bestSellingProductId", null);
            return summary;
        }

        // 计算总销量
        int totalSales = salesDataList.stream().mapToInt(SalesData::getSales).sum();
        
        // 计算总销售额
        BigDecimal totalRevenue = salesDataList.stream()
                .map(data -> {
                    BigDecimal price = data.getPrice();
                    if (price == null) {
                        return BigDecimal.ZERO;
                    }
                    return price.multiply(BigDecimal.valueOf(data.getSales()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 计算日均销量
        long daysBetween = ChronoUnit.DAYS.between(finalStartDate, finalEndDate) + 1;
        int averageDailySales = (int) (totalSales / daysBetween);
        
        // 统计产品数量
        long productCount = salesDataList.stream()
                .map(SalesData::getProductId)
                .distinct()
                .count();
        
        // 找出最畅销产品
        Map<Long, Integer> productSalesMap = salesDataList.stream()
                .collect(Collectors.groupingBy(
                        SalesData::getProductId,
                        Collectors.summingInt(SalesData::getSales)
                ));
        
        Map.Entry<Long, Integer> bestSellingProduct = productSalesMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        
        String bestSellingProductName = "无";
        Long bestSellingProductId = null;
        if (bestSellingProduct != null) {
            bestSellingProductId = bestSellingProduct.getKey();
            Product product = productMapper.selectById(bestSellingProductId);
            if (product != null) {
                bestSellingProductName = product.getName();
            }
        }

        summary.put("totalSales", totalSales);
        summary.put("totalRevenue", totalRevenue);
        summary.put("averageDailySales", averageDailySales);
        summary.put("productCount", productCount);
        summary.put("bestSellingProduct", bestSellingProductName);
        summary.put("bestSellingProductId", bestSellingProductId);
        summary.put("dateRange", finalStartDate + " 至 " + finalEndDate);
        summary.put("days", daysBetween);

        return summary;
    }

    private Double calculateGrowthRate(Long productId, String period, LocalDate currentStart, LocalDate currentEnd) {
        try {
            // 计算上一个周期
            LocalDate previousStart, previousEnd;
            long daysBetween = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1;
            
            previousEnd = currentStart.minusDays(1);
            previousStart = previousEnd.minusDays(daysBetween - 1);
            
            // 查询上一个周期的销售数据
            List<SalesData> previousSalesData = salesDataMapper.selectProductSalesByDateRange(
                    productId, previousStart, previousEnd);
            
            List<SalesData> currentSalesData = salesDataMapper.selectProductSalesByDateRange(
                    productId, currentStart, currentEnd);
            
            // 计算上一个周期的总销量
            int previousTotalSales = previousSalesData.stream().mapToInt(SalesData::getSales).sum();
            int currentTotalSales = currentSalesData.stream().mapToInt(SalesData::getSales).sum();
            
            if (previousTotalSales == 0) {
                return currentTotalSales > 0 ? 100.0 : 0.0; // 如果上一个周期为0，当前有销量则增长率为100%
            }
            
            double growthRate = ((double) (currentTotalSales - previousTotalSales) / previousTotalSales) * 100;
            return Math.round(growthRate * 100.0) / 100.0; // 保留两位小数
        } catch (Exception e) {
            log.error("计算增长率失败: {}", productId, e);
            return 0.0;
        }
    }
}