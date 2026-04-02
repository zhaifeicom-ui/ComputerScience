package com.saas.sales;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
// uvicorn AiPredict.ai_service:app --host 0.0.0.0 --port 5000
// PS D:\aiproject\Test (2)\Test\frontend> npm run dev
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class
})
@MapperScan("com.saas.sales.mapper")
@EnableCaching
@EnableAsync
@EnableScheduling
public class SalesPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesPlatformApplication.class, args);
    }
}