package com.saas.sales.controller;

import com.saas.sales.dto.ApiResponse;
import com.saas.sales.dto.SalesTrendResponse;
import com.saas.sales.dto.TopProductResponse;
import com.saas.sales.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@Tag(name = "分析接口", description = "销售数据统计分析接口")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/sales-trend")
    @Operation(summary = "获取销量趋势", description = "按日、周、月获取销量趋势数据")
    public ApiResponse<List<SalesTrendResponse>> getSalesTrend(
            @Parameter(description = "统计周期: daily, weekly, monthly", example = "daily")
            @RequestParam(defaultValue = "daily") String period,
            
            @Parameter(description = "开始日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate startDate,
            
            @Parameter(description = "结束日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate endDate,
            
            @Parameter(description = "返回数据条数限制")
            @RequestParam(required = false) 
            Integer limit) {
        
        try {
            List<SalesTrendResponse> trends = analysisService.getSalesTrend(period, startDate, endDate, limit);
            return ApiResponse.success("获取销量趋势成功", trends);
        } catch (Exception e) {
            return ApiResponse.error(500, "获取销量趋势失败: " + e.getMessage());
        }
    }

    @GetMapping("/top-products")
    @Operation(summary = "获取畅销商品", description = "获取指定时间段内的畅销商品排名")
    public ApiResponse<List<TopProductResponse>> getTopProducts(
            @Parameter(description = "统计周期: daily, weekly, monthly", example = "daily")
            @RequestParam(defaultValue = "daily") String period,
            
            @Parameter(description = "开始日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate startDate,
            
            @Parameter(description = "结束日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate endDate,
            
            @Parameter(description = "返回数据条数限制")
            @RequestParam(required = false) 
            Integer limit) {
        
        try {
            List<TopProductResponse> topProducts = analysisService.getTopProducts(period, startDate, endDate, limit);
            return ApiResponse.success("获取畅销商品成功", topProducts);
        } catch (Exception e) {
            return ApiResponse.error(500, "获取畅销商品失败: " + e.getMessage());
        }
    }

    @GetMapping("/sales-summary")
    @Operation(summary = "获取销售概览", description = "获取指定时间段的销售数据概览")
    public ApiResponse<Map<String, Object>> getSalesSummary(
            @Parameter(description = "开始日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate startDate,
            
            @Parameter(description = "结束日期, 格式: yyyy-MM-dd")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate endDate) {
        
        try {
            Map<String, Object> summary = analysisService.getSalesSummary(startDate, endDate);
            return ApiResponse.success("获取销售概览成功", summary);
        } catch (Exception e) {
            return ApiResponse.error(500, "获取销售概览失败: " + e.getMessage());
        }
    }

    @GetMapping("/comparison")
    @Operation(summary = "销售对比分析", description = "对比两个时间段的销售数据")
    public ApiResponse<Map<String, Object>> getSalesComparison(
            @Parameter(description = "第一个时间段的开始日期, 格式: yyyy-MM-dd")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1Start,
            
            @Parameter(description = "第一个时间段的结束日期, 格式: yyyy-MM-dd")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1End,
            
            @Parameter(description = "第二个时间段的开始日期, 格式: yyyy-MM-dd")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2Start,
            
            @Parameter(description = "第二个时间段的结束日期, 格式: yyyy-MM-dd")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2End) {
        
        try {
            // 获取第一个时间段的销售概览
            Map<String, Object> summary1 = analysisService.getSalesSummary(period1Start, period1End);
            
            // 获取第二个时间段的销售概览
            Map<String, Object> summary2 = analysisService.getSalesSummary(period2Start, period2End);
            
            // 计算增长率
            Map<String, Object> comparison = new java.util.HashMap<>();
            comparison.put("period1", summary1);
            comparison.put("period2", summary2);
            
            int period1Sales = (int) summary1.get("totalSales");
            int period2Sales = (int) summary2.get("totalSales");
            double salesGrowthRate = period1Sales == 0 ? 100.0 : 
                ((double) (period2Sales - period1Sales) / period1Sales) * 100;
            
            BigDecimal period1Revenue = (BigDecimal) summary1.get("totalRevenue");
            BigDecimal period2Revenue = (BigDecimal) summary2.get("totalRevenue");
            double revenueGrowthRate = period1Revenue.compareTo(BigDecimal.ZERO) == 0 ? 100.0 :
                period2Revenue.subtract(period1Revenue)
                    .divide(period1Revenue, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            
            comparison.put("salesGrowthRate", Math.round(salesGrowthRate * 100.0) / 100.0);
            comparison.put("revenueGrowthRate", Math.round(revenueGrowthRate * 100.0) / 100.0);
            
            return ApiResponse.success("获取销售对比成功", comparison);
        } catch (Exception e) {
            return ApiResponse.error(500, "获取销售对比失败: " + e.getMessage());
        }
    }
}