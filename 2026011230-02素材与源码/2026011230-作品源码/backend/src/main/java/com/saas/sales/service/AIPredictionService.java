package com.saas.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.sales.entity.SalesData;
import com.saas.sales.mapper.SalesDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
public class AIPredictionService {

    private final RestTemplate restTemplate;
    private static final int SALES_VALID_WINDOW_DAYS = 300;
    private static final int SALES_RECENT_ACTIVITY_DAYS = 60;
    private static final int SALES_MIN_VALID_POINTS = 50;
    private static final int SALES_MIN_QUANTITY = 1;

    @Autowired
    private SalesDataMapper salesDataMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${ai.prediction.service.url:http://localhost:5000}")
    private String aiServiceUrl;
    
    public AIPredictionService() {
        this.restTemplate = new RestTemplate();
    }
    
    // ==========================================
    // 销量预测 (Sales Forecasting)
    // ==========================================
    
    public void trainSalesForecast(String tenantId, String productId) {
        String url = aiServiceUrl + "/v1/models/sales-forecast/train";
        Map<String, Object> request = buildTrainingRequest(tenantId, productId);
        sendPostRequest(url, request, Object.class);
    }
    
    public Map<String, Object> predictSalesForecast(String tenantId, String productId, int daysToForecast, List<Map<String, Object>> futureRegressors) {
        if (daysToForecast <= 0) {
            throw new RuntimeException("预测天数必须大于 0。");
        }
        String url = aiServiceUrl + "/v1/predict/sales-forecast";
        Map<String, Object> request = buildTrainingRequest(tenantId, productId);
        request.put("days_to_forecast", daysToForecast);
        request.put("future_regressors", futureRegressors);
        
        return sendPostRequest(url, request, Map.class);
    }

    // ==========================================
    // 价格弹性分析 (Price Elasticity)
    // ==========================================
    
    public void trainPriceElasticity(String tenantId, String productId) {
        String url = aiServiceUrl + "/v1/models/price-elasticity/train";
        Map<String, Object> request = buildTrainingRequest(tenantId, productId);
        sendPostRequest(url, request, Object.class);
    }
    
    public Map<String, Object> predictPriceElasticity(String tenantId, String productId, Map<String, Object> simulationContext, Map<String, Object> baselineContext) {
        String url = aiServiceUrl + "/v1/predict/price-elasticity";
        Map<String, Object> request = buildTrainingRequest(tenantId, productId); // Provide historical_data for auto-training
        request.put("simulation_context", simulationContext);
        if (baselineContext != null) {
            request.put("baseline_context", baselineContext);
        }
        
        return sendPostRequest(url, request, Map.class);
    }
    
    // ==========================================
    // 未来销量预测总和 (Future Sales Prediction Sum)
    // ==========================================
    
    public Double predictFutureSalesTotal(String tenantId, String productId, int daysToForecast) {
        if (daysToForecast <= 0) {
            throw new RuntimeException("预测天数必须大于 0。");
        }
        
        Map<String, Object> predictionResult = predictSalesForecast(tenantId, productId, daysToForecast, null);
        
        // 解析预测结果，计算总销售量
        // 假设返回结果包含 "predictions" 列表，每个元素有 "yhat" 字段
        List<Map<String, Object>> predictions = (List<Map<String, Object>>) predictionResult.get("predictions");
        if (predictions == null || predictions.isEmpty()) {
            return 0.0;
        }
        
        double totalSales = 0.0;
        for (Map<String, Object> pred : predictions) {
            Object yhatObj = pred.get("yhat");
            if (yhatObj instanceof Number) {
                totalSales += ((Number) yhatObj).doubleValue();
            }
        }
        
        return totalSales;
    }
    
    // ==========================================
    // 通用辅助方法
    // ==========================================
    
    private Map<String, Object> buildTrainingRequest(String tenantId, String productId) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(SALES_VALID_WINDOW_DAYS);
        LambdaQueryWrapper<SalesData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesData::getTenantId, Long.parseLong(tenantId))
                    .eq(SalesData::getProductId, Long.parseLong(productId))
                    .ge(SalesData::getDate, windowStart)
                    .orderByAsc(SalesData::getDate);
        
        List<SalesData> salesDataList = salesDataMapper.selectList(queryWrapper);

        // 聚合每天的销售数据
        Map<LocalDate, Map<String, Object>> aggregatedDataMap = new java.util.LinkedHashMap<>();
        for (SalesData data : salesDataList) {
            LocalDate date = data.getDate();
            if (!aggregatedDataMap.containsKey(date)) {
                Map<String, Object> record = new HashMap<>();
                record.put("ds", date.toString());
                record.put("y", data.getSales() != null ? data.getSales() : 0);
                // 对于价格，我们可以取平均值或者最新的一条记录，这里简单取最后一条或者如果有就不覆盖
                record.put("price", data.getPrice()); 
                record.put("price_sum", data.getPrice() != null ? data.getPrice().doubleValue() : 0.0);
                record.put("count", 1);
                aggregatedDataMap.put(date, record);
            } else {
                Map<String, Object> record = aggregatedDataMap.get(date);
                int currentSales = (int) record.get("y");
                record.put("y", currentSales + (data.getSales() != null ? data.getSales() : 0));
                
                if (data.getPrice() != null) {
                    double currentPriceSum = (double) record.get("price_sum");
                    record.put("price_sum", currentPriceSum + data.getPrice().doubleValue());
                    int count = (int) record.get("count") + 1;
                    record.put("count", count);
                    // 价格取平均
                    record.put("price", java.math.BigDecimal.valueOf((currentPriceSum + data.getPrice().doubleValue()) / count));
                }
            }
        }

        // 过滤掉销量小于最小值的记录，并转换为列表
        List<Map<String, Object>> historicalData = new ArrayList<>();
        for (Map<String, Object> record : aggregatedDataMap.values()) {
            if ((int) record.get("y") > SALES_MIN_QUANTITY) {
                // 移除不需要传给AI的辅助字段
                record.remove("price_sum");
                record.remove("count");
                historicalData.add(record);
            }
        }

        if (historicalData.size() < SALES_MIN_VALID_POINTS) {
            throw new RuntimeException(
                    "近期有效销售数据不足：仅统计近 " + SALES_VALID_WINDOW_DAYS +
                    " 天且聚合后销量大于 " + SALES_MIN_QUANTITY +
                    " 的记录，当前仅 " + historicalData.size() +
                    " 条，至少需要 " + SALES_MIN_VALID_POINTS + " 条。"
            );
        }

        String latestDateStr = (String) historicalData.get(historicalData.size() - 1).get("ds");
        LocalDate latestDate = LocalDate.parse(latestDateStr);
        if (latestDate == null || latestDate.isBefore(today.minusDays(SALES_RECENT_ACTIVITY_DAYS))) {
            throw new RuntimeException(
                    "缺少近期销售数据：最近有效销售日期为 " + latestDate +
                    "，距今超过 " + SALES_RECENT_ACTIVITY_DAYS + " 天，暂不支持预测。"
            );
        }
        
        Map<String, Object> request = new HashMap<>();
        request.put("tenant_id", tenantId);
        request.put("product_id", productId);
        request.put("historical_data", historicalData);
        
        return request;
    }

    private <T> T sendPostRequest(String url, Object requestBody, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("AI服务返回错误: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("AI服务调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }
}
