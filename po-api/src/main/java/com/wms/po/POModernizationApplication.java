package com.wms.po;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * PO Modernization Application
 *
 * Migrates legacy PO stored procedures to Java microservices
 * using Temporal workflow orchestration with Saga pattern.
 *
 * Key features:
 * - Temporal workflow orchestration
 * - Saga pattern for distributed transactions
 * - Variation layer for V0/V2, regions, clients
 * - Plugin system for client-specific logic
 * - Dual-write for gradual migration
 */
@SpringBootApplication(scanBasePackages = "com.wms.po")
@EnableAsync
public class POModernizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(POModernizationApplication.class, args);
    }
}
