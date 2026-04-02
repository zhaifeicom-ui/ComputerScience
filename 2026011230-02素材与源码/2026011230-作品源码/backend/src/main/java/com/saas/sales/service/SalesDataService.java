package com.saas.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.dto.SalesDataCreateRequest;
import com.saas.sales.dto.SalesDataResponse;
import com.saas.sales.dto.SalesDataUpdateRequest;
import com.saas.sales.entity.Product;
import com.saas.sales.entity.SalesData;
import com.saas.sales.mapper.ProductMapper;
import com.saas.sales.mapper.SalesDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SalesDataService {

    @Autowired
    private SalesDataMapper salesDataMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private com.saas.sales.mapper.InventoryMapper inventoryMapper;

    public Page<SalesDataResponse> getSalesData(int page, int size, Long productId, String startDate, String endDate) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Page<SalesData> salesDataPage = new Page<>(page, size);
        LambdaQueryWrapper<SalesData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(SalesData::getDate);

        if (productId != null) {
            queryWrapper.eq(SalesData::getProductId, productId);
        }

        // 日期范围过滤
        if (startDate != null && !startDate.isEmpty()) {
            queryWrapper.ge(SalesData::getDate, java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryWrapper.le(SalesData::getDate, java.time.LocalDate.parse(endDate));
        }

        Page<SalesData> result = salesDataMapper.selectPage(salesDataPage, queryWrapper);

        // 转换为响应DTO
        Page<SalesDataResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<SalesDataResponse> salesDataResponses = result.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(salesDataResponses);

        return responsePage;
    }

    public SalesDataResponse getSalesDataById(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        SalesData salesData = salesDataMapper.selectById(id);
        if (salesData == null) {
            throw new RuntimeException("销售数据不存在");
        }

        return convertToResponse(salesData);
    }

    @Transactional
    public SalesDataResponse createSalesData(SalesDataCreateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 验证产品是否存在且属于当前租户
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || product.getDeleted() == 1) {
            throw new RuntimeException("产品不存在");
        }

        // 验证库存
        LambdaQueryWrapper<com.saas.sales.entity.Inventory> invWrapper = new LambdaQueryWrapper<>();
        invWrapper.eq(com.saas.sales.entity.Inventory::getTenantId, tenantId)
                  .eq(com.saas.sales.entity.Inventory::getProductId, request.getProductId());
        com.saas.sales.entity.Inventory inventory = inventoryMapper.selectOne(invWrapper);
        if (inventory == null) {
            throw new RuntimeException("该产品暂无库存记录，无法导入销售数据");
        }
        if (request.getSales() > inventory.getStock()) {
            throw new RuntimeException("导入的销售数量(" + request.getSales() + ")大于当前商品库存(" + inventory.getStock() + ")");
        }

        // 创建销售数据
        SalesData salesData = new SalesData();
        salesData.setTenantId(tenantId);
        salesData.setProductId(request.getProductId());
        salesData.setDate(request.getDate());
        salesData.setSales(request.getSales());
        salesData.setPrice(request.getPrice());
        salesData.setCreatedAt(LocalDateTime.now());

        salesDataMapper.insert(salesData);

        // 销售数据创建成功后，自动更新库存
        // 使用销售数据的创建时间进行判断
        LocalDateTime createTime = salesData.getCreatedAt() != null ? salesData.getCreatedAt() : LocalDateTime.now();
        inventoryService.updateInventoryFromSales(request.getProductId(), createTime, request.getSales());

        return convertToResponse(salesData);
    }

    @Transactional
    public SalesDataResponse updateSalesData(Long id, SalesDataUpdateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        SalesData salesData = salesDataMapper.selectById(id);
        if (salesData == null) {
            throw new RuntimeException("销售数据不存在");
        }

        // 验证产品是否存在且属于当前租户（如果更新了产品ID）
        if (request.getProductId() != null && !request.getProductId().equals(salesData.getProductId())) {
            Product product = productMapper.selectById(request.getProductId());
            if (product == null || product.getDeleted() == 1) {
                throw new RuntimeException("产品不存在");
            }
        }

        // 保存原始销售数据用于库存计算
        Integer originalSales = salesData.getSales();
        java.time.LocalDate originalDate = salesData.getDate();
        
        // 如果更新了销售数量，验证库存是否足够
        if (request.getSales() != null && !request.getSales().equals(originalSales)) {
            Long targetProductId = request.getProductId() != null ? request.getProductId() : salesData.getProductId();
            LambdaQueryWrapper<com.saas.sales.entity.Inventory> invWrapper = new LambdaQueryWrapper<>();
            invWrapper.eq(com.saas.sales.entity.Inventory::getTenantId, tenantId)
                      .eq(com.saas.sales.entity.Inventory::getProductId, targetProductId);
            com.saas.sales.entity.Inventory inventory = inventoryMapper.selectOne(invWrapper);
            if (inventory == null) {
                throw new RuntimeException("该产品暂无库存记录，无法更新销售数据");
            }
            
            int diff = request.getSales() - originalSales;
            // 如果是增加销售量，检查当前库存是否足够扣减这个差值
            if (diff > 0 && diff > inventory.getStock()) {
                throw new RuntimeException("增加的销售数量(" + diff + ")大于当前商品库存(" + inventory.getStock() + ")");
            }
        }

        // 更新销售数据
        if (request.getDate() != null) {
            salesData.setDate(request.getDate());
        }
        if (request.getSales() != null) {
            salesData.setSales(request.getSales());
        }
        if (request.getPrice() != null) {
            salesData.setPrice(request.getPrice());
        }

        salesDataMapper.updateById(salesData);
        
        // 处理库存更新逻辑
        handleInventoryUpdateForSalesData(
            salesData.getProductId(),
            originalDate, originalSales,
            salesData.getDate(), salesData.getSales()
        );

        return convertToResponse(salesData);
    }

    @Transactional
    public void deleteSalesData(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        SalesData salesData = salesDataMapper.selectById(id);
        if (salesData == null) {
            throw new RuntimeException("销售数据不存在");
        }

        // 删除销售数据前，恢复库存（如果适用）
        // 将销售日期转换为LocalDateTime（当天开始时间）
        LocalDateTime salesDateTime = salesData.getDate().atStartOfDay();
        // 注意：恢复库存意味着增加库存数量
        inventoryService.updateInventoryFromSales(
            salesData.getProductId(), 
            salesDateTime, 
            -salesData.getSales()  // 负数表示恢复库存
        );

        salesDataMapper.deleteById(id);
        log.info("删除销售数据: {}", id);
    }
    
    /**
     * 处理销售数据变化对库存的影响
     * @param productId 产品ID
     * @param originalDate 原始销售日期
     * @param originalSales 原始销售量
     * @param newDate 新销售日期
     * @param newSales 新销售量
     */
    private void handleInventoryUpdateForSalesData(
            Long productId,
            java.time.LocalDate originalDate,
            Integer originalSales,
            java.time.LocalDate newDate,
            Integer newSales) {
        
        try {
            // 情况1：销售日期不变，销售数量变化
            if (originalDate.equals(newDate)) {
                int salesChange = newSales - originalSales;
                if (salesChange != 0) {
                    // 销售日期不变，只需要调整库存数量
                    LocalDateTime salesDateTime = newDate.atStartOfDay();
                    inventoryService.updateInventoryFromSales(productId, salesDateTime, salesChange);
                }
            }
            // 情况2：销售日期变化
            else {
                // 先恢复原始日期的库存（如果原始销售日期晚于库存最后更新时间）
                LocalDateTime originalDateTime = originalDate.atStartOfDay();
                inventoryService.updateInventoryFromSales(productId, originalDateTime, -originalSales);
                
                // 再扣减新日期的库存（如果新销售日期晚于库存最后更新时间）
                LocalDateTime newDateTime = newDate.atStartOfDay();
                inventoryService.updateInventoryFromSales(productId, newDateTime, newSales);
            }
            
        } catch (Exception e) {
            log.error("处理销售数据库存更新失败，产品ID: {}, 错误: {}", productId, e.getMessage(), e);
            // 这里可以选择是否抛出异常，为了不影响销售数据更新，我们只记录错误
        }
    }

    private SalesDataResponse convertToResponse(SalesData salesData) {
        SalesDataResponse response = new SalesDataResponse();
        response.setId(salesData.getId());
        response.setProductId(salesData.getProductId());
        response.setDate(salesData.getDate());
        response.setSales(salesData.getSales());
        response.setPrice(salesData.getPrice());
        response.setCreatedAt(salesData.getCreatedAt());

        // 获取产品名称
        Product product = productMapper.selectById(salesData.getProductId());
        if (product != null) {
            response.setProductName(product.getName());
        }
        
        return response;
    }
}