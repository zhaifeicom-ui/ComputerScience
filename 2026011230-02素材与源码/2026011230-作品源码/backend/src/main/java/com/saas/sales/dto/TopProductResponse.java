package com.saas.sales.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopProductResponse {
    private Long productId;
    private String productName;
    private Integer totalSales;
    private BigDecimal totalRevenue;
    private Double growthRate; // 增长率
    
    // 构造函数
    public TopProductResponse(Long productId, String productName, Integer totalSales, BigDecimal totalRevenue, Double growthRate) {
        this.productId = productId;
        this.productName = productName;
        this.totalSales = totalSales;
        this.totalRevenue = totalRevenue;
        this.growthRate = growthRate;
    }
    
    public TopProductResponse() {
    }
}