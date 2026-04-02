package com.saas.sales.service;

import com.saas.sales.dto.InventoryCheckRequest;
import com.saas.sales.entity.Inventory;
import com.saas.sales.mapper.InventoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class InventoryScheduler {

    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private InventoryMapper inventoryMapper;
    
    @Value("${inventory.check.future-days:30}")
    private Integer defaultFutureDays;
    
    @Value("${inventory.check.scheduled.enabled:true}")
    private Boolean scheduledCheckEnabled;
    
    @Value("${inventory.check.scheduled.cron:0 0 2 * * ?}")
    private String scheduledCheckCron;
    
    @Value("${inventory.check.notification.enabled:false}")
    private Boolean notificationEnabled;
    
    @Value("${inventory.check.notification.email.enabled:false}")
    private Boolean emailNotificationEnabled;
    
    @Value("${inventory.check.notification.email.recipients:}")
    private List<String> emailRecipients;
    
    @Value("${inventory.check.notification.sms.enabled:false}")
    private Boolean smsNotificationEnabled;
    
    @Value("${inventory.check.notification.sms.recipients:}")
    private List<String> smsRecipients;
    
    /**
     * 每天凌晨2点执行库存检查定时任务
     * 检查所有产品的未来销量预测与库存情况
     */
    @Scheduled(cron = "${inventory.check.scheduled.cron:0 0 2 * * ?}")
    @Transactional(readOnly = true)
    public void scheduleInventoryCheck() {
        if (!scheduledCheckEnabled) {
            log.debug("库存检查定时任务已禁用");
            return;
        }
        
        log.info("开始执行定时库存检查任务，未来天数: {}", defaultFutureDays);
        long startTime = System.currentTimeMillis();
        
        try {
            // 获取所有库存记录并按用户分组
            List<Inventory> allInventories = inventoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>()
            );
            
            if (allInventories.isEmpty()) {
                log.info("没有需要检查的库存记录");
                return;
            }
            
            // 按用户ID分组库存记录
            Map<Long, List<Inventory>> inventoriesByUser = allInventories.stream()
                .filter(inv -> inv.getTenantId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Inventory::getTenantId));
            
            int totalCheckedCount = 0;
            int totalWarningCount = 0;
            int userCount = inventoriesByUser.size();
            
            log.info("开始为 {} 个用户执行库存检查", userCount);
            
            // 为每个用户执行库存检查
            for (Map.Entry<Long, List<Inventory>> entry : inventoriesByUser.entrySet()) {
                Long userId = entry.getKey();
                List<Inventory> userInventories = entry.getValue();
                
                log.debug("开始检查用户 {} 的库存，共 {} 个产品", userId, userInventories.size());
                
                // 设置当前线程的租户上下文
                com.saas.sales.config.TenantContextHolder.setTenantId(userId);
                
                try {
                    int userCheckedCount = 0;
                    int userWarningCount = 0;
                    
                    // 检查该用户下的每个产品
                    for (Inventory inventory : userInventories) {
                        try {
                            InventoryCheckRequest request = new InventoryCheckRequest();
                            request.setProductId(inventory.getProductId());
                            request.setFutureDays(defaultFutureDays);
                            
                            Map<String, Object> result = inventoryService.checkInventoryForFutureSales(request);
                            
                            userCheckedCount++;
                            totalCheckedCount++;
                            
                            boolean hasWarnings = (Boolean) result.get("hasWarnings");
                            if (hasWarnings) {
                                userWarningCount++;
                                totalWarningCount++;
                                List<Map<String, Object>> warnings = (List<Map<String, Object>>) result.get("warnings");
                                
                                // 记录警告日志
                                for (Map<String, Object> warning : warnings) {
                                    Long productId = (Long) warning.get("productId");
                                    String productName = (String) warning.get("productName");
                                    Double predictedSales = (Double) warning.get("predictedSales");
                                    Integer availableStock = (Integer) warning.get("availableStock");
                                    Double shortage = (Double) warning.get("shortage");
                                    
                                    log.warn("库存预警 - 用户ID: {}, 产品ID: {}, 产品名称: {}, 预测销量: {}, 可用库存: {}, 缺口: {}",
                                            userId, productId, productName, predictedSales, availableStock, shortage);
                                }
                                
                                // 可以在这里添加通知逻辑，比如发送邮件、短信或系统通知
                                if (notificationEnabled) {
                                    sendInventoryWarningNotification(result);
                                }
                            }
                            
                        } catch (Exception e) {
                            log.error("用户 {} 的产品ID {} 库存检查失败: {}", 
                                    userId, inventory.getProductId(), e.getMessage(), e);
                        }
                    }
                    
                    log.debug("用户 {} 库存检查完成，检查了 {} 个产品，发现 {} 个预警",
                            userId, userCheckedCount, userWarningCount);
                    
                } finally {
                    // 清理租户上下文
                    com.saas.sales.config.TenantContextHolder.clear();
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("定时库存检查任务完成，共检查了 {} 个用户，{} 个产品，发现 {} 个预警，耗时 {} ms",
                    userCount, totalCheckedCount, totalWarningCount, elapsedTime);
            
        } catch (Exception e) {
            log.error("定时库存检查任务执行失败", e);
        }
    }
    
    /**
     * 每30分钟执行一次快速库存检查
     * 只检查低库存的紧急情况
     */
    @Scheduled(cron = "0 */30 * * * ?")
    @Transactional(readOnly = true)
    public void scheduleQuickInventoryCheck() {
        if (!scheduledCheckEnabled) {
            return;
        }
        log.debug("执行快速库存检查");
        try {
            // 查找库存量低于安全库存的产品
            List<Inventory> lowInventories = inventoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Inventory>()
                    .lt(Inventory::getStock, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Inventory>query()
                        .getSqlSelect() + ".safetyStock")
            );
            
            if (!lowInventories.isEmpty()) {
                log.warn("发现 {} 个产品库存低于安全库存", lowInventories.size());
                for (Inventory inventory : lowInventories) {
                    log.warn("紧急库存预警 - 产品ID: {}, 当前库存: {}, 安全库存: {}, 缺口: {}",
                            inventory.getProductId(), inventory.getStock(),
                            inventory.getSafetyStock(), inventory.getSafetyStock() - inventory.getStock());
                }
            }
            
        } catch (Exception e) {
            log.error("快速库存检查失败", e);
        }
    }
    
    /**
     * 每周日凌晨1点执行库存数据清理任务
     * 清理已删除的库存记录（逻辑删除超过30天的记录）
     */
    @Scheduled(cron = "0 0 1 ? * SUN")
    @Transactional
    public void scheduleInventoryCleanup() {
        log.info("开始执行库存数据清理任务");
        
        try {
            // 新的表结构已删除deleted和updatedAt字段，清理任务暂时禁用
            log.info("由于表结构变更（已删除deleted和updatedAt字段），库存数据清理任务已禁用");
            
            // 未来可根据新的业务需求重新实现清理逻辑，例如：
            // 1. 清理库存为0且最新更新时间超过一定时间的记录
            // 2. 根据业务规则清理无效的库存记录
            
        } catch (Exception e) {
            log.error("库存数据清理任务失败", e);
        }
    }
    
    /**
     * 发送库存预警通知
     */
    private void sendInventoryWarningNotification(Map<String, Object> checkResult) {
        if (checkResult == null) {
            log.warn("库存预警通知：检查结果为空，跳过通知");
            return;
        }
        
        Object futureDaysObj = checkResult.get("futureDays");
        Object warningsObj = checkResult.get("warnings");
        
        if (futureDaysObj == null || warningsObj == null) {
            log.warn("库存预警通知：检查结果缺少必要字段");
            return;
        }
        
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) warningsObj;
        Integer futureDays = (Integer) futureDaysObj;
        
        if (warnings.isEmpty()) {
            log.debug("库存预警通知：没有预警需要发送");
            return;
        }
        
        // 记录详细的预警信息到日志
        log.warn("===== 库存预警通知开始 =====");
        log.warn("未来 {} 天，发现 {} 个产品库存不足", futureDays, warnings.size());
        
        for (Map<String, Object> warning : warnings) {
            Long productId = (Long) warning.get("productId");
            String productName = (String) warning.get("productName");
            Double predictedSales = (Double) warning.get("predictedSales");
            Integer availableStock = (Integer) warning.get("availableStock");
            Double shortage = (Double) warning.get("shortage");
            
            log.warn("预警产品 - ID: {}, 名称: {}, 预测销量: {}, 可用库存: {}, 缺口: {}",
                    productId, productName, predictedSales, availableStock, shortage);
        }
        
        log.warn("===== 库存预警通知结束 =====");
        
        // 发送邮件通知（如果启用）
        if (Boolean.TRUE.equals(emailNotificationEnabled) && emailRecipients != null && !emailRecipients.isEmpty()) {
            sendEmailNotification(checkResult, warnings, futureDays);
        } else {
            log.debug("邮件通知未启用或没有收件人");
        }
        
        // 发送短信通知（如果启用）
        if (Boolean.TRUE.equals(smsNotificationEnabled) && smsRecipients != null && !smsRecipients.isEmpty()) {
            sendSmsNotification(checkResult, warnings, futureDays);
        } else {
            log.debug("短信通知未启用或没有收件人");
        }
        
        // 这里可以添加其他通知方式，如：
        // 1. 推送系统通知到前端（WebSocket）
        // 2. 写入预警记录到数据库
        // 3. 发送消息队列通知
        
        log.info("库存预警通知处理完成，共处理 {} 个预警", warnings.size());
    }
    
    /**
     * 发送邮件通知（待实现）
     */
    private void sendEmailNotification(Map<String, Object> checkResult, List<Map<String, Object>> warnings, Integer futureDays) {
        // 这里可以实现邮件发送逻辑
        // 示例：使用JavaMailSender发送邮件
        log.info("邮件通知功能待实现 - 未来 {} 天，发现 {} 个产品库存不足", futureDays, warnings.size());
        
        // 实际实现代码示例：
        // try {
        //     MimeMessage message = mailSender.createMimeMessage();
        //     MimeMessageHelper helper = new MimeMessageHelper(message, true);
        //     helper.setTo(emailRecipients.toArray(new String[0]));
        //     helper.setSubject("库存预警通知 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        //     
        //     StringBuilder content = new StringBuilder();
        //     content.append("<h2>库存预警通知</h2>");
        //     content.append("<p>未来 ").append(futureDays).append(" 天，发现 ").append(warnings.size()).append(" 个产品库存不足：</p>");
        //     content.append("<table border='1'><tr><th>产品ID</th><th>产品名称</th><th>预测销量</th><th>可用库存</th><th>缺口</th></tr>");
        //     
        //     for (Map<String, Object> warning : warnings) {
        //         content.append("<tr>");
        //         content.append("<td>").append(warning.get("productId")).append("</td>");
        //         content.append("<td>").append(warning.get("productName")).append("</td>");
        //         content.append("<td>").append(warning.get("predictedSales")).append("</td>");
        //         content.append("<td>").append(warning.get("availableStock")).append("</td>");
        //         content.append("<td>").append(warning.get("shortage")).append("</td>");
        //         content.append("</tr>");
        //     }
        //     
        //     content.append("</table>");
        //     helper.setText(content.toString(), true);
        //     
        //     mailSender.send(message);
        //     log.info("库存预警邮件已发送给 {} 个收件人", emailRecipients.size());
        // } catch (Exception e) {
        //     log.error("发送库存预警邮件失败", e);
        // }
    }
    
    /**
     * 发送短信通知（待实现）
     */
    private void sendSmsNotification(Map<String, Object> checkResult, List<Map<String, Object>> warnings, Integer futureDays) {
        // 这里可以实现短信发送逻辑
        log.info("短信通知功能待实现 - 未来 {} 天，发现 {} 个产品库存不足", futureDays, warnings.size());
        
        // 实际实现代码示例：
        // try {
        //     for (String recipient : smsRecipients) {
        //         // 调用短信服务API
        //         String message = String.format("库存预警：未来%d天，发现%d个产品库存不足，请及时处理。",
        //                 futureDays, warnings.size());
        //         log.debug("发送短信给 {}: {}", recipient, message);
        //     }
        //     log.info("库存预警短信已发送给 {} 个收件人", smsRecipients.size());
        // } catch (Exception e) {
        //     log.error("发送库存预警短信失败", e);
        // }
    }
    
    /**
     * 手动触发库存检查（供API调用）
     * 
     * @param futureDays 未来天数，如果为null则使用默认值
     * @return 库存检查结果
     */
    public Map<String, Object> triggerManualInventoryCheck(Integer futureDays) {
        try {
            if (futureDays != null && futureDays <= 0) {
                throw new IllegalArgumentException("未来天数必须大于0");
            }
            
            int daysToCheck = futureDays != null ? futureDays : defaultFutureDays;
            log.info("手动触发库存检查，未来天数: {}", daysToCheck);
            
            InventoryCheckRequest request = new InventoryCheckRequest();
            request.setFutureDays(daysToCheck);
            
            Map<String, Object> result = inventoryService.checkInventoryForFutureSales(request);
            
            // 如果启用了通知，发送手动检查的预警通知
            if (Boolean.TRUE.equals(notificationEnabled)) {
                boolean hasWarnings = (Boolean) result.get("hasWarnings");
                if (hasWarnings) {
                    log.info("手动库存检查发现预警，发送通知");
                    sendInventoryWarningNotification(result);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("手动触发库存检查失败", e);
            Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("hasWarnings", false);
            errorResult.put("warnings", java.util.Collections.emptyList());
            return errorResult;
        }
    }
}