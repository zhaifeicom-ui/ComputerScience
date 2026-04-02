package com.saas.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AIPredictionRequest {
    private Long productId;
    private String productName;
    private String category;
    private List<HistoricalSalesData> historicalData;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer forecastPeriod; // 预测周期天数
    
    @Data
    public static class HistoricalSalesData {
        private LocalDate date;
        private Integer quantity;
        private BigDecimal revenue;
        private Integer stock;
    }
}