package com.saas.sales.controller;

import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.dto.ApiResponse;
import com.saas.sales.service.AIPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-predictions")
@Tag(name = "AI预测接口", description = "基于AI的销售预测与价格弹性分析接口")
@Slf4j
public class AIPredictionController {

    @Autowired
    private AIPredictionService aiPredictionService;

    // ==========================================
    // 销量预测 (Sales Forecasting)
    // ==========================================

    @PostMapping("/train/sales-forecast")
    @Operation(summary = "触发销量预测模型训练", description = "在后台触发销量预测模型训练")
    public ApiResponse<Void> trainSalesForecast(@RequestParam String productId) {
        try {
            String tenantId = String.valueOf(TenantContextHolder.getTenantId());
            aiPredictionService.trainSalesForecast(tenantId, productId);
            return ApiResponse.success("销量预测模型训练任务已提交", null);
        } catch (Exception e) {
            log.error("触发销量预测训练失败", e);
            return ApiResponse.error(500, "触发训练失败: " + e.getMessage());
        }
    }

    @PostMapping("/predict/sales-forecast")
    @Operation(summary = "获取销量预测", description = "基于商品ID获取销量预测")
    public ApiResponse<Map<String, Object>> predictSalesForecast(@RequestBody Map<String, Object> request) {
        try {
            String tenantId = String.valueOf(TenantContextHolder.getTenantId());
            String productId = request.get("product_id").toString();
            Number daysToForecastNumber = (Number) request.get("days_to_forecast");
            int daysToForecast = daysToForecastNumber.intValue();
            List<Map<String, Object>> futureRegressors = (List<Map<String, Object>>) request.get("future_regressors");
            if (futureRegressors == null) {
                futureRegressors = List.of();
            }
            
            Map<String, Object> prediction = aiPredictionService.predictSalesForecast(tenantId, productId, daysToForecast, futureRegressors);
            return ApiResponse.success("预测成功", prediction);
        } catch (Exception e) {
            log.error("销量预测失败", e);
            return ApiResponse.error(500, "预测失败: " + e.getMessage());
        }
    }

    // ==========================================
    // 价格弹性分析 (Price Elasticity)
    // ==========================================

    @PostMapping("/train/price-elasticity")
    @Operation(summary = "触发价格弹性模型训练", description = "在后台触发价格弹性模型训练")
    public ApiResponse<Void> trainPriceElasticity(@RequestParam String productId) {
        try {
            String tenantId = String.valueOf(TenantContextHolder.getTenantId());
            aiPredictionService.trainPriceElasticity(tenantId, productId);
            return ApiResponse.success("价格弹性模型训练任务已提交", null);
        } catch (Exception e) {
            log.error("触发价格弹性训练失败", e);
            return ApiResponse.error(500, "触发训练失败: " + e.getMessage());
        }
    }

    @PostMapping("/predict/price-elasticity")
    @Operation(summary = "获取价格弹性分析", description = "模拟新价格对销量的影响")
    public ApiResponse<Map<String, Object>> predictPriceElasticity(@RequestBody Map<String, Object> request) {
        try {
            String tenantId = String.valueOf(TenantContextHolder.getTenantId());
            String productId = request.get("product_id").toString();
            Map<String, Object> simulationContext = (Map<String, Object>) request.get("simulation_context");
            Map<String, Object> baselineContext = (Map<String, Object>) request.get("baseline_context");
            
            Map<String, Object> analysis = aiPredictionService.predictPriceElasticity(tenantId, productId, simulationContext, baselineContext);
            return ApiResponse.success("分析成功", analysis);
        } catch (Exception e) {
            log.error("价格弹性分析失败", e);
            return ApiResponse.error(500, "分析失败: " + e.getMessage());
        }
    }
}
