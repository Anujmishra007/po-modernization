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

    // Separate task queues to avoid activity registration conflicts
    private static final String POPULATE_TASK_QUEUE = "po-populate-queue";
    private static final String FINALIZE_TASK_QUEUE = "po-finalize-queue";

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
            // Create separate workers for each workflow type to avoid activity conflicts

            // Worker for Populate PO workflow
            Worker populateWorker = workerFactory.newWorker(POPULATE_TASK_QUEUE);
            populateWorker.registerWorkflowImplementationTypes(PopulatePOWorkflowImpl.class);
            populateWorker.registerActivitiesImplementations(populatePOActivities);

            // Worker for Finalize Receipt workflow
            Worker finalizeWorker = workerFactory.newWorker(FINALIZE_TASK_QUEUE);
            finalizeWorker.registerWorkflowImplementationTypes(FinalizeReceiptWorkflowImpl.class);
            finalizeWorker.registerActivitiesImplementations(finalizeReceiptActivities);

            workerFactory.start();
            workerStarted = true;
            log.info("Temporal workers started on task queues: {} (Populate), {} (Finalize)",
                     POPULATE_TASK_QUEUE, FINALIZE_TASK_QUEUE);
        }
    }

    @PreDestroy
    public void stopWorker() {
        if (workerFactory != null && workerStarted) {
            workerFactory.shutdown();
            log.info("Temporal workers stopped");
        }
    }

    /**
     * Returns the task queue for Populate PO workflows.
     */
    public static String getPopulateTaskQueue() {
        return POPULATE_TASK_QUEUE;
    }

    /**
     * Returns the task queue for Finalize Receipt workflows.
     */
    public static String getFinalizeTaskQueue() {
        return FINALIZE_TASK_QUEUE;
    }
}
