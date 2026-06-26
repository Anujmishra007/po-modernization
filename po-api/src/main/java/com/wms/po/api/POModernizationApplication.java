package com.wms.po.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.wms.po")
@EntityScan(basePackages = "com.wms.po")
@EnableJpaRepositories(basePackages = "com.wms.po")
public class POModernizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(POModernizationApplication.class, args);
    }
}
