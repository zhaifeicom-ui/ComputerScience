package com.saas.sales.controller;

import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.constants.mqConstants;
import com.saas.sales.dto.ApiResponse;
import com.saas.sales.dto.JobStatusResponse;
import com.saas.sales.entity.DataImportJob;
import com.saas.sales.entity.ImportJobStatus;
import com.saas.sales.service.DataImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType; 
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import com.saas.sales.dto.DataimportMessage;
import com.saas.sales.dto.SalesDataCreateRequest;
import com.saas.sales.dto.SalesDataResponse;
import com.saas.sales.service.SalesDataService;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "数据导入任务接口", description = "CSV数据导入任务管理接口")
@Slf4j
public class JobController {

    @Autowired
    private DataImportService dataImportService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private SalesDataService salesDataService;

    @PostMapping(value = "/upload-data",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传数据文件(CSV/Excel)", description = "上传CSV或Excel文件进行数据导入，创建导入任务")
    public ApiResponse<Long> uploadData(
            @Parameter(description = "数据文件", required = true)
            @RequestPart("file") MultipartFile file) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return ApiResponse.error(400, "缺少租户ID");
        }

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".csv") && !fileName.toLowerCase().endsWith(".xlsx"))) {
            return ApiResponse.error(400, "只支持 CSV 或 XLSX(Excel) 文件");
        }

        try {
            Long jobId = dataImportService.createImportJob(fileName, tenantId);
            
            // 将 MultipartFile 的内容读取为字节数组，防止异步线程执行时临时文件被清理
            byte[] fileBytes = file.getBytes();
            
            // 异步处理文件
            DataimportMessage message = new DataimportMessage(jobId, tenantId, fileBytes, fileName);
            rabbitTemplate.convertAndSend(mqConstants.LOAD_EXCHANGE, mqConstants.LOAD_KEY, message);
            // dataImportService.processImportData(jobId, fileBytes, fileName, tenantId);
            log.info("创建导入任务: {}，租户: {}，文件: {}", jobId, tenantId, fileName);
            return ApiResponse.success("文件上传成功，任务已创建", jobId);
        } catch (Exception e) {
            log.error("创建导入任务失败", e);
            return ApiResponse.error(500, "创建导入任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/single-sale")
    @Operation(summary = "导入单条销售数据", description = "创建单条销售数据")
    public ApiResponse<SalesDataResponse> addSingleSale(
            @RequestBody(description = "单条销售数据请求")
            @Validated @org.springframework.web.bind.annotation.RequestBody SalesDataCreateRequest request) {
        try {
            SalesDataResponse salesData = salesDataService.createSalesData(request);
            return ApiResponse.success("导入成功", salesData);
        } catch (Exception e) {
            log.error("导入单条销售数据失败", e);
            return ApiResponse.error(400, "导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "获取导入任务状态", description = "根据任务ID获取数据导入任务的状态信息")
    public ApiResponse<JobStatusResponse> getJobStatus(
            @Parameter(description = "任务ID", example = "1") @PathVariable Long jobId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return ApiResponse.error(400, "缺少租户ID");
        }

        DataImportJob job = dataImportService.getJobById(jobId);
        if (job == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        // 检查任务是否属于当前租户
        if (!job.getTenantId().equals(tenantId)) {
            return ApiResponse.error(403, "无权访问此任务");
        }

        JobStatusResponse response = convertToResponse(job);
        return ApiResponse.success(response);
    }

    @GetMapping
    @Operation(summary = "获取导入任务列表", description = "获取当前租户的数据导入任务列表")
    public ApiResponse<List<JobStatusResponse>> getJobs() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return ApiResponse.error(400, "缺少租户ID");
        }

        List<DataImportJob> jobs = dataImportService.getJobsByTenant(tenantId);
        List<JobStatusResponse> responses = jobs.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "删除导入任务", description = "删除指定的导入任务记录")
    public ApiResponse<Void> deleteJob(
            @Parameter(description = "任务ID", example = "1") @PathVariable Long jobId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return ApiResponse.error(400, "缺少租户ID");
        }
        
        try {
            dataImportService.deleteJob(jobId, tenantId);
            return ApiResponse.success("任务删除成功", null);
        } catch (Exception e) {
            log.error("删除导入任务失败: {}", jobId, e);
            return ApiResponse.error(400, "删除失败: " + e.getMessage());
        }
    }

    private JobStatusResponse convertToResponse(DataImportJob job) {
        JobStatusResponse response = new JobStatusResponse();
        response.setJobId(job.getId());
        response.setFileName(job.getFileName());
        response.setStatus(ImportJobStatus.valueOf(job.getStatus()));
        response.setSuccessCount(job.getSuccessCount());
        response.setFailureCount(job.getFailureCount());
        response.setErrorReportUrl(job.getErrorReportUrl());
        response.setCreatedAt(job.getCreatedAt());
        response.setFinishedAt(job.getFinishedAt());

        // 计算进度（简单估算）
        if (job.getStatus().equals("COMPLETED") || job.getStatus().equals("FAILED")) {
            response.setProgress(100.0);
        } else if (job.getSuccessCount() + job.getFailureCount() > 0) {
            // 假设总行数未知，使用成功+失败计数作为指示
            response.setProgress(50.0);
        } else {
            response.setProgress(0.0);
        }

        response.setMessage("状态: " + job.getStatus());
        return response;
    }
}