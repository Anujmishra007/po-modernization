package com.wms.po.config;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

/**
 * Mock Temporal configuration for local development when Temporal server is not available.
 * This configuration provides mock beans to allow the application to start without Temporal.
 */
@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "false")
@Slf4j
public class TemporalMockConfig {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.warn("Temporal is disabled - using mock WorkflowServiceStubs");
        return mock(WorkflowServiceStubs.class);
    }

    @Bean
    public WorkflowClient workflowClient() {
        log.warn("Temporal is disabled - using mock WorkflowClient. Workflow operations will not work!");
        return mock(WorkflowClient.class);
    }

    @Bean
    public WorkerFactory workerFactory() {
        log.warn("Temporal is disabled - using mock WorkerFactory");
        return mock(WorkerFactory.class);
    }
}
