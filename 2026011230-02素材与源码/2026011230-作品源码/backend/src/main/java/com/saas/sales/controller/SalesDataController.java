package com.saas.sales.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.dto.ApiResponse;
import com.saas.sales.dto.SalesDataCreateRequest;
import com.saas.sales.dto.SalesDataResponse;
import com.saas.sales.dto.SalesDataUpdateRequest;
import com.saas.sales.service.SalesDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@Tag(name = "销售数据管理接口", description = "销售数据管理接口")
@Slf4j
public class SalesDataController {

    @Autowired
    private SalesDataService salesDataService;

    @GetMapping
    @Operation(summary = "获取销售数据列表", description = "分页获取销售数据，支持按产品和日期范围筛选")
    public ApiResponse<Page<SalesDataResponse>> getSalesData(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "产品ID") @RequestParam(required = false) Long productId,
            @Parameter(description = "开始日期，格式: yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式: yyyy-MM-dd") @RequestParam(required = false) String endDate) {
        try {
            Page<SalesDataResponse> salesData = salesDataService.getSalesData(page, size, productId, startDate, endDate);
            return ApiResponse.success(salesData);
        } catch (Exception e) {
            log.error("获取销售数据失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取销售数据", description = "根据销售数据ID获取详细信息")
    public ApiResponse<SalesDataResponse> getSalesDataById(
            @Parameter(description = "销售数据ID", example = "1") @PathVariable Long id) {
        try {
            SalesDataResponse salesData = salesDataService.getSalesDataById(id);
            return ApiResponse.success(salesData);
        } catch (Exception e) {
            log.error("获取销售数据失败: {}", id, e);
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建销售数据", description = "创建新的销售数据记录")
    public ApiResponse<SalesDataResponse> createSalesData(
            @RequestBody(description = "销售数据创建请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody SalesDataCreateRequest request) {
        try {
            SalesDataResponse salesData = salesDataService.createSalesData(request);
            return ApiResponse.success("创建成功", salesData);
        } catch (Exception e) {
            log.error("创建销售数据失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新销售数据", description = "根据ID更新销售数据")
    public ApiResponse<SalesDataResponse> updateSalesData(
            @Parameter(description = "销售数据ID", example = "1") @PathVariable Long id,
            @RequestBody(description = "销售数据更新请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody SalesDataUpdateRequest request) {
        try {
            SalesDataResponse salesData = salesDataService.updateSalesData(id, request);
            return ApiResponse.success("更新成功", salesData);
        } catch (Exception e) {
            log.error("更新销售数据失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除销售数据", description = "根据ID删除销售数据")
    public ApiResponse<Void> deleteSalesData(
            @Parameter(description = "销售数据ID", example = "1") @PathVariable Long id) {
        try {
            salesDataService.deleteSalesData(id);
            return ApiResponse.<Void>success("删除成功", null);
        } catch (Exception e) {
            log.error("删除销售数据失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }
}