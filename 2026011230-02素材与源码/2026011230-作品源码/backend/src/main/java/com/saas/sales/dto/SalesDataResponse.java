package com.saas.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SalesDataResponse {
    private Long id;
    private Long productId;
    private String productName;
    private LocalDate date;
    private Integer sales;
    private BigDecimal price;
    private LocalDateTime createdAt;
}