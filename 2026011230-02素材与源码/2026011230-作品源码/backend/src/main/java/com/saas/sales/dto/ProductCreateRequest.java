package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "商品名称不能为空")
    private String name;
    
    private String category;
}