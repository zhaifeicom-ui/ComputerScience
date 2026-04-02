package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;

@Data
public class InventoryUpdateRequest {
    @Min(value = 0, message = "库存数量不能小于0")
    private Integer stock;
    
    @Min(value = 0, message = "安全库存不能小于0")
    private Integer safetyStock;
}