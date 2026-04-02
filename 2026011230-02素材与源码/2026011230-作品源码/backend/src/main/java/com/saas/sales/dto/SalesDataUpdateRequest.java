package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesDataUpdateRequest {
    private Long productId;
    
    private LocalDate date;
    
    @Positive(message = "销量必须为正数")
    private Integer sales;
    
    private BigDecimal price;
}