package com.saas.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesTrendResponse {
    private LocalDate date;
    private Integer totalSales;
    private BigDecimal totalRevenue;
    private Integer productCount;
    
    // 构造函数
    public SalesTrendResponse(LocalDate date, Integer totalSales, BigDecimal totalRevenue, Integer productCount) {
        this.date = date;
        this.totalSales = totalSales;
        this.totalRevenue = totalRevenue;
        this.productCount = productCount;
    }
    
    public SalesTrendResponse() {
    }
}