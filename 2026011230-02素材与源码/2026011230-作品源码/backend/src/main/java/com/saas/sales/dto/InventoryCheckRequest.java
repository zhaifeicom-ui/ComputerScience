package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class InventoryCheckRequest {
    @NotNull(message = "未来天数不能为空")
    @Min(value = 1, message = "未来天数必须大于0")
    private Integer futureDays;
    
    private Long productId;
}