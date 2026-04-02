package com.saas.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AIPredictionResponse {
    private Long productId;
    private String productName;
    private String category;
    private List<PredictionData> predictions;
    private BigDecimal totalPredictedRevenue;
    private Integer totalPredictedQuantity;
    private Double confidenceScore;
    private String modelVersion;
    private LocalDate generatedAt;
    
    @Data
    public static class PredictionData {
        private LocalDate date;
        private Integer predictedQuantity;
        private BigDecimal predictedRevenue;
        private Double confidence;
        private String trend; // UP, DOWN, STABLE
        private String recommendation;
    }
}