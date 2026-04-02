package com.saas.sales.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String category;
    private LocalDateTime createdAt;
}