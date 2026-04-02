package com.saas.sales.listener;
import org.springframework.beans.factory.annotation.Autowired;
import com.saas.sales.constants.mqConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import com.saas.sales.dto.DataimportMessage;
import com.saas.sales.service.DataImportService;
@Component
public class loadListener {
    
    @Autowired
    private DataImportService dataImportService;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = mqConstants.LOAD_QUEUE),
            exchange = @Exchange(name = mqConstants.LOAD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = mqConstants.LOAD_KEY
    ))
    public void LoadCSV(DataimportMessage message) {
        Long jobId = message.getJobId();
        Long tenantId = message.getTenantId();
        byte[] fileBytes = message.getFileBytes();
        String fileName = message.getFileName();
        dataImportService.processImportData(jobId, fileBytes, fileName, tenantId);
    }
}
