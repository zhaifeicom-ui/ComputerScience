package com.saas.sales.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sales_data")
public class SalesData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long productId;
    private LocalDate date;
    private Integer sales;
    private BigDecimal price;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}