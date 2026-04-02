package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class ProductUpdateRequest {
    @NotBlank(message = "商品名称不能为空")
    private String name;
    
    private String category;
}