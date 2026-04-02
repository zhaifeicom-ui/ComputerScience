package com.saas.sales.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_import_job")
public class DataImportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String fileName;
    private String status;
    private Integer successCount;
    private Integer failureCount;
    private String errorReportUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}