package com.wms.po.config;

import com.wms.po.activity.FinalizeReceiptActivities;
import com.wms.po.activity.PopulatePOActivities;
import com.wms.po.workflow.impl.FinalizeReceiptWorkflowImpl;
import com.wms.po.workflow.impl.PopulatePOWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class TemporalConfig {

    @Value("${temporal.server.host:localhost}")
    private String temporalHost;

    @Value("${temporal.server.port:7233}")
    private int temporalPort;

    @Value("${temporal.namespace:default}")
    private String namespace;

    private static final String TASK_QUEUE = "po-task-queue";

    private final PopulatePOActivities populatePOActivities;
    private final FinalizeReceiptActivities finalizeReceiptActivities;

    private WorkerFactory workerFactory;
    private boolean workerStarted = false;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        String target = temporalHost + ":" + temporalPort;
        log.info("Connecting to Temporal server at: {}", target);

        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(target)
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build()
        );
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        this.workerFactory = WorkerFactory.newInstance(workflowClient);
        return workerFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorker() {
        if (workerFactory != null && !workerStarted) {
            Worker worker = workerFactory.newWorker(TASK_QUEUE);

            // Register workflow implementations
            worker.registerWorkflowImplementationTypes(
                PopulatePOWorkflowImpl.class,
                FinalizeReceiptWorkflowImpl.class
            );

            // Register activity implementations
            worker.registerActivitiesImplementations(populatePOActivities);
            worker.registerActivitiesImplementations(finalizeReceiptActivities);

            workerFactory.start();
            workerStarted = true;
            log.info("Temporal worker started on task queue: {} with Populate and Finalize workflows", TASK_QUEUE);
        }
    }

    @PreDestroy
    public void stopWorker() {
        if (workerFactory != null && workerStarted) {
            workerFactory.shutdown();
            log.info("Temporal worker stopped");
        }
    }
}
