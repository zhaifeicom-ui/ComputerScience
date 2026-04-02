package com.saas.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesDataCreateRequest {
    @NotNull(message = "产品ID不能为空")
    private Long productId;
    
    @NotNull(message = "日期不能为空")
    private LocalDate date;
    
    @NotNull(message = "销量不能为空")
    @Positive(message = "销量必须为正数")
    private Integer sales;
    
    private BigDecimal price;
}