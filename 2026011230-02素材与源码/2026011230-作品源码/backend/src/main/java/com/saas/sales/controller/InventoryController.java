package com.saas.sales.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.dto.*;
import com.saas.sales.service.InventoryService;
import com.saas.sales.service.InventoryScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventories")
@Tag(name = "库存管理接口", description = "库存信息管理与未来销量预测检查接口")
@Slf4j
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private InventoryScheduler inventoryScheduler;

    @GetMapping
    @Operation(summary = "获取库存列表", description = "分页获取库存信息，支持按产品筛选")
    public ApiResponse<Page<InventoryResponse>> getInventories(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "产品ID") @RequestParam(required = false) Long productId) {
        try {
            Page<InventoryResponse> inventories = inventoryService.getInventories(page, size, productId);
            return ApiResponse.success(inventories);
        } catch (Exception e) {
            log.error("获取库存列表失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取库存", description = "根据库存ID获取详细信息")
    public ApiResponse<InventoryResponse> getInventory(
            @Parameter(description = "库存ID", example = "1") @PathVariable Long id) {
        try {
            InventoryResponse inventory = inventoryService.getInventoryById(id);
            return ApiResponse.success(inventory);
        } catch (Exception e) {
            log.error("获取库存失败: {}", id, e);
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建库存", description = "创建新的库存记录")
    public ApiResponse<InventoryResponse> createInventory(
            @RequestBody(description = "库存创建请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody InventoryCreateRequest request) {
        try {
            InventoryResponse inventory = inventoryService.createInventory(request);
            return ApiResponse.success("创建成功", inventory);
        } catch (Exception e) {
            log.error("创建库存失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新库存", description = "根据ID更新库存信息")
    public ApiResponse<InventoryResponse> updateInventory(
            @Parameter(description = "库存ID", example = "1") @PathVariable Long id,
            @RequestBody(description = "库存更新请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody InventoryUpdateRequest request) {
        try {
            InventoryResponse inventory = inventoryService.updateInventory(id, request);
            return ApiResponse.success("更新成功", inventory);
        } catch (Exception e) {
            log.error("更新库存失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除库存", description = "根据ID删除库存记录")
    public ApiResponse<Void> deleteInventory(
            @Parameter(description = "库存ID", example = "1") @PathVariable Long id) {
        try {
            inventoryService.deleteInventory(id);
            return ApiResponse.<Void>success("删除成功", null);
        } catch (Exception e) {
            log.error("删除库存失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/check-future-sales")
    @Operation(summary = "检查未来销量预测", description = "检查未来N天销量预测是否超过可用库存")
    public ApiResponse<Map<String, Object>> checkInventoryForFutureSales(
            @RequestBody(description = "库存检查请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody InventoryCheckRequest request) {
        try {
            Map<String, Object> result = inventoryService.checkInventoryForFutureSales(request);
            return ApiResponse.success("检查完成", result);
        } catch (Exception e) {
            log.error("检查库存失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    @GetMapping("/trigger-manual-check")
    @Operation(summary = "手动触发定时库存检查", description = "手动触发定时库存检查任务，可以指定未来天数，不指定则使用默认值")
    public ApiResponse<Map<String, Object>> triggerManualInventoryCheck(
            @Parameter(description = "未来天数，不指定则使用配置的默认值") 
            @RequestParam(required = false) Integer futureDays) {
        try {
            Map<String, Object> result = inventoryScheduler.triggerManualInventoryCheck(futureDays);
            return ApiResponse.success("手动检查已触发", result);
        } catch (Exception e) {
            log.error("手动触发库存检查失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }
}