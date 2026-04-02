package com.saas.sales.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitmqConfig {
    @Bean
    public MessageConverter jsonMessageConverter(){
        // 发送消息和接收消息都自动转为 JSON 格式
        return new Jackson2JsonMessageConverter();
    }
}
