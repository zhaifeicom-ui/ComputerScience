package com.saas.sales.dto;

import com.saas.sales.entity.ImportJobStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobStatusResponse {
    private Long jobId;
    private String fileName;
    private ImportJobStatus status;
    private int successCount;
    private int failureCount;
    private String errorReportUrl;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private double progress; // 0-100
    private String message;
}