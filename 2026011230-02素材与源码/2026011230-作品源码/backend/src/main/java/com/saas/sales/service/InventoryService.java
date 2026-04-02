package com.saas.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.dto.*;
import com.saas.sales.entity.Inventory;
import com.saas.sales.entity.Product;
import com.saas.sales.mapper.InventoryMapper;
import com.saas.sales.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@CacheConfig(cacheNames = "inventory")
public class InventoryService {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AIPredictionService aiPredictionService;

    @Cacheable(value = "inventory-page", key = "T(com.saas.sales.config.TenantContextHolder).getTenantId() + ':' + #page + '-' + #size + '-' + #productId")
    public Page<InventoryResponse> getInventories(int page, int size, Long productId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Page<Inventory> inventoryPage = new Page<>(page, size);
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getTenantId, tenantId)
                   .orderByDesc(Inventory::getLatestUpdateOn);

        if (productId != null) {
            queryWrapper.eq(Inventory::getProductId, productId);
        }

        Page<Inventory> result = inventoryMapper.selectPage(inventoryPage, queryWrapper);

        // 获取产品名称映射
        List<Long> productIds = result.getRecords().stream()
                .map(Inventory::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> productNameMap = getProductNameMap(productIds);

        // 转换为响应DTO
        Page<InventoryResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<InventoryResponse> inventoryResponses = result.getRecords().stream()
                .map(inventory -> convertToResponse(inventory, productNameMap))
                .collect(Collectors.toList());
        responsePage.setRecords(inventoryResponses);

        return responsePage;
    }

    @Cacheable(key = "T(com.saas.sales.config.TenantContextHolder).getTenantId() + ':' + #id")
    public InventoryResponse getInventoryById(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Inventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在");
        }

        // 获取产品名称
        String productName = getProductName(inventory.getProductId());
        InventoryResponse response = convertToResponse(inventory);
        response.setProductName(productName);
        return response;
    }

    @Transactional
    @CacheEvict(value = "inventory-page", allEntries = true)
    public InventoryResponse createInventory(InventoryCreateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 检查产品是否存在
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || product.getDeleted() == 1) {
            throw new RuntimeException("产品不存在");
        }

        // 检查是否已存在该产品的库存记录
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getTenantId, tenantId)
                   .eq(Inventory::getProductId, request.getProductId());
        Inventory existingInventory = inventoryMapper.selectOne(queryWrapper);
        if (existingInventory != null) {
            throw new RuntimeException("该产品已存在库存记录");
        }

        // 创建库存记录
        Inventory inventory = new Inventory();
        inventory.setTenantId(tenantId);
        inventory.setProductId(request.getProductId());
        inventory.setStock(request.getStock());
        inventory.setSafetyStock(request.getSafetyStock());
        inventory.setLatestUpdateOn(LocalDateTime.now());
        
        inventoryMapper.insert(inventory);
        
        // 获取产品名称
        String productName = product.getName();
        InventoryResponse response = convertToResponse(inventory);
        response.setProductName(productName);
        return response;
    }

    @Transactional
    @CacheEvict(value = "inventory-page", allEntries = true)
    public InventoryResponse updateInventory(Long id, InventoryUpdateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Inventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在");
        }

        // 验证库存记录是否属于当前租户
        if (!inventory.getTenantId().equals(tenantId)) {
            throw new RuntimeException("无权修改该库存记录");
        }

        // 更新库存数量
        if (request.getStock() != null) {
            inventory.setStock(request.getStock());
        }
        
        // 更新安全库存
        if (request.getSafetyStock() != null) {
            inventory.setSafetyStock(request.getSafetyStock());
        }
        
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        // 获取产品名称
        String productName = getProductName(inventory.getProductId());
        InventoryResponse response = convertToResponse(inventory);
        response.setProductName(productName);
        return response;
    }

    @Transactional
    @CacheEvict(value = "inventory-page", allEntries = true)
    public void deleteInventory(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Inventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在");
        }
        if (!inventory.getTenantId().equals(tenantId)) {
            throw new RuntimeException("无权删除该库存记录");
        }

        inventoryMapper.deleteById(id);
    }

    public Map<String, Object> checkInventoryForFutureSales(InventoryCheckRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 验证请求参数
        if (request.getFutureDays() == null || request.getFutureDays() <= 0) {
            throw new RuntimeException("未来天数必须大于0");
        }

        log.info("开始检查库存预测，租户ID: {}，未来天数: {}，产品ID: {}", 
                 tenantId, request.getFutureDays(), request.getProductId());

        // 获取库存记录
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getTenantId, tenantId);
        if (request.getProductId() != null) {
            queryWrapper.eq(Inventory::getProductId, request.getProductId());
        }
        List<Inventory> inventories = inventoryMapper.selectList(queryWrapper);

        // 检查每个产品的库存
        Map<String, Object> result = new java.util.HashMap<>();
        List<Map<String, Object>> warnings = new java.util.ArrayList<>();
        List<Map<String, Object>> skippedProducts = new java.util.ArrayList<>();

        for (Inventory inventory : inventories) {
            Long productId = inventory.getProductId();
            Double predictedSales = null;
            
            try {
                // 调用AI预测服务获取该产品未来N天的销售量预测总和
                predictedSales = aiPredictionService.predictFutureSalesTotal(
                        tenantId.toString(), productId.toString(), request.getFutureDays());
            } catch (Exception e) {
                log.warn("产品ID {} 销量预测失败: {}", productId, e.getMessage());
                Map<String, Object> skipped = new java.util.HashMap<>();
                skipped.put("productId", productId);
                skipped.put("productName", getProductName(productId));
                skipped.put("error", e.getMessage());
                skippedProducts.add(skipped);
                continue;
            }
            
            if (predictedSales == null || predictedSales <= 0) {
                continue;
            }

            int availableStock = inventory.getStock() - inventory.getSafetyStock();
            if (availableStock < 0) {
                availableStock = 0;
            }

            if (predictedSales > availableStock) {
                Map<String, Object> warning = new java.util.HashMap<>();
                warning.put("productId", productId);
                warning.put("productName", getProductName(productId));
                warning.put("currentStock", inventory.getStock());
                warning.put("safetyStock", inventory.getSafetyStock());
                warning.put("availableStock", availableStock);
                warning.put("predictedSales", predictedSales);
                warning.put("futureDays", request.getFutureDays());
                warning.put("shortage", predictedSales - availableStock);
                warnings.add(warning);
            }
        }

        result.put("hasWarnings", !warnings.isEmpty());
        result.put("warnings", warnings);
        result.put("skippedProducts", skippedProducts);
        result.put("futureDays", request.getFutureDays());
        result.put("checkedAt", LocalDateTime.now());
        
        log.info("库存预测检查完成，租户ID: {}，共检查{}个产品，发现{}个警告，{}个产品预测失败",
                 tenantId, inventories.size(), warnings.size(), skippedProducts.size());
        return result;
    }

    @Transactional
    @CacheEvict(value = "inventory-page", allEntries = true)
    public void updateInventoryFromSales(Long productId, LocalDateTime salesDataCreatedAt, Integer salesQuantity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return;
        }

        // 查找该产品的库存记录
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getTenantId, tenantId)
                   .eq(Inventory::getProductId, productId);
        Inventory inventory = inventoryMapper.selectOne(queryWrapper);
        if (inventory == null) {
            log.warn("产品ID {} 没有库存记录，无法更新", productId);
            return;
        }

        // 决定是否更新库存的逻辑：
        // 1. 如果销售数量为正数（扣减库存）：只有当销售数据的创建时间晚于库存的最后更新日期时才更新
        // 2. 如果销售数量为负数（恢复库存）：总是更新，因为这是纠正之前的扣减
        // 3. 如果最后更新时间为null（新库存记录）：总是更新
        boolean shouldUpdate = false;
        
        if (salesQuantity < 0) {
            // 恢复库存，总是更新
            shouldUpdate = true;
            log.debug("产品ID {} 恢复库存，忽略日期检查", productId);
        } else if (inventory.getLatestUpdateOn() == null) {
            // 新库存记录，总是更新
            shouldUpdate = true;
            log.debug("产品ID {} 库存记录无最后更新时间，允许更新", productId);
        } else if (salesDataCreatedAt != null && salesDataCreatedAt.isAfter(inventory.getLatestUpdateOn())) {
            // 销售数据的创建时间晚于库存的最后更新时间，允许扣减库存
            shouldUpdate = true;
            log.debug("产品ID {} 销售数据的创建时间晚于最后更新时间，允许更新", productId);
        } else {
            log.debug("产品ID {} 销售数据的创建时间不满足更新条件，跳过库存更新", productId);
            return;
        }

        if (shouldUpdate) {
            // 更新库存数量
            int newStock = inventory.getStock() - salesQuantity;
            if (newStock < 0) {
                newStock = 0;
                log.warn("产品ID {} 库存扣减后为负，已设置为0", productId);
            }
            
            // 只有扣减库存（正数）时才更新最后更新时间
            if (salesQuantity > 0) {
                inventory.setLatestUpdateOn(salesDataCreatedAt);
            }
            
            inventory.setStock(newStock);
            inventoryMapper.updateById(inventory);
            
            String action = salesQuantity > 0 ? "扣减" : (salesQuantity < 0 ? "恢复" : "无变化");
            log.info("产品ID {} 库存已{}，销售数量：{}，新库存：{}", productId, action, salesQuantity, newStock);
        }
    }

    private String getProductName(Long productId) {
        Product product = productMapper.selectById(productId);
        return product != null ? product.getName() : "未知产品";
    }

    private Map<Long, String> getProductNameMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Product::getId, productIds);
        List<Product> products = productMapper.selectList(queryWrapper);
        return products.stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
    }

    private InventoryResponse convertToResponse(Inventory inventory) {
        InventoryResponse response = new InventoryResponse();
        BeanUtils.copyProperties(inventory, response);
        return response;
    }

    private InventoryResponse convertToResponse(Inventory inventory, Map<Long, String> productNameMap) {
        InventoryResponse response = convertToResponse(inventory);
        response.setProductName(productNameMap.getOrDefault(inventory.getProductId(), "未知产品"));
        return response;
    }
}