package com.saas.sales.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.dto.ApiResponse;
import com.saas.sales.dto.ProductCreateRequest;
import com.saas.sales.dto.ProductResponse;
import com.saas.sales.dto.ProductUpdateRequest;
import com.saas.sales.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "商品管理接口", description = "商品信息管理接口")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    @Operation(summary = "获取商品列表", description = "分页获取商品信息")
    public ApiResponse<Page<ProductResponse>> getProducts(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") int size) {
        try {
            Page<ProductResponse> products = productService.getProducts(page, size);
            return ApiResponse.success(products);
        } catch (Exception e) {
            log.error("获取商品列表失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有商品列表", description = "获取所有商品信息（不分页）")
    public ApiResponse<List<ProductResponse>> getAllProducts() {
        try {
            List<ProductResponse> products = productService.getAllProducts();
            return ApiResponse.success(products);
        } catch (Exception e) {
            log.error("获取所有商品列表失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    @Operation(summary = "根据ID获取商品", description = "根据商品ID获取详细信息")
    public ApiResponse<ProductResponse> getProduct(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id) {
        try {
            ProductResponse product = productService.getProductById(id);
            return ApiResponse.success(product);
        } catch (Exception e) {
            log.error("获取商品失败: {}", id, e);
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建商品", description = "创建新的商品信息")
    public ApiResponse<ProductResponse> createProduct(
            @RequestBody(description = "商品创建请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody ProductCreateRequest request) {
        try {
            ProductResponse product = productService.createProduct(request);
            return ApiResponse.success("创建成功", product);
        } catch (Exception e) {
            log.error("创建商品失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新商品", description = "根据ID更新商品信息")
    public ApiResponse<ProductResponse> updateProduct(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id,
            @RequestBody(description = "商品更新请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody ProductUpdateRequest request) {
        try {
            ProductResponse product = productService.updateProduct(id, request);
            return ApiResponse.success("更新成功", product);
        } catch (Exception e) {
            log.error("更新商品失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "根据ID删除商品")
    public ApiResponse<Void> deleteProduct(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ApiResponse.<Void>success("删除成功", null);
        } catch (Exception e) {
            log.error("删除商品失败: {}", id, e);
            return ApiResponse.error(400, e.getMessage());
        }
    }
}