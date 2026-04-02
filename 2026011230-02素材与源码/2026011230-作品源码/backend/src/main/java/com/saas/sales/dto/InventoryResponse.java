package com.saas.sales.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer stock;
    private Integer safetyStock;
    private LocalDateTime latestUpdateOn;
}