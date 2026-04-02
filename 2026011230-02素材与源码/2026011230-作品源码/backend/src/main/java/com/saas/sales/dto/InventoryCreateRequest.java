package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class InventoryCreateRequest {
    @NotNull(message = "产品ID不能为空")
    private Long productId;
    
    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能小于0")
    private Integer stock;
    
    @NotNull(message = "安全库存不能为空")
    @Min(value = 0, message = "安全库存不能小于0")
    private Integer safetyStock;
}