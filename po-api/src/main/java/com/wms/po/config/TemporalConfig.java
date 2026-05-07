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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class TemporalConfig {

    @Value("${temporal.server.host:localhost}")
    private String temporalHost;

    @Value("${temporal.server.port:7233}")
    private int temporalPort;

    @Value("${temporal.namespace:default}")
    private String namespace;

    @Value("${temporal.startup.retry-attempts:5}")
    private int retryAttempts;

    @Value("${temporal.startup.retry-delay-seconds:5}")
    private int retryDelaySeconds;

    @Value("${temporal.startup.fail-on-error:false}")
    private boolean failOnError;

    // Separate task queues to avoid activity registration conflicts
    private static final String POPULATE_TASK_QUEUE = "po-populate-queue";
    private static final String FINALIZE_TASK_QUEUE = "po-finalize-queue";

    private WorkerFactory workerFactory;
    private boolean workerStarted = false;
    private PopulatePOActivities populatePOActivities;
    private FinalizeReceiptActivities finalizeReceiptActivities;

    @Bean
    @Lazy
    public WorkflowServiceStubs workflowServiceStubs() {
        String target = temporalHost + ":" + temporalPort;
        log.info("Creating lazy WorkflowServiceStubs for Temporal server at: {}", target);

        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(target)
                        .build()
        );
    }

    @Bean
    @Lazy
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build()
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorker(ApplicationReadyEvent event) {
        // Get activity beans from application context
        this.populatePOActivities = event.getApplicationContext().getBean(PopulatePOActivities.class);
        this.finalizeReceiptActivities = event.getApplicationContext().getBean(FinalizeReceiptActivities.class);

        if (workerStarted) {
            return;
        }

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                log.info("Attempting to connect to Temporal and start workers (attempt {}/{})", attempt, retryAttempts);

                // Create connection to Temporal
                String target = temporalHost + ":" + temporalPort;
                WorkflowServiceStubs serviceStubs = WorkflowServiceStubs.newServiceStubs(
                        WorkflowServiceStubsOptions.newBuilder()
                                .setTarget(target)
                                .build()
                );

                WorkflowClient client = WorkflowClient.newInstance(
                        serviceStubs,
                        WorkflowClientOptions.newBuilder()
                                .setNamespace(namespace)
                                .build()
                );

                WorkerFactory factory = WorkerFactory.newInstance(client);

                // Create separate workers for each workflow type to avoid activity conflicts
                // Worker for Populate PO workflow
                Worker populateWorker = factory.newWorker(POPULATE_TASK_QUEUE);
                populateWorker.registerWorkflowImplementationTypes(PopulatePOWorkflowImpl.class);
                populateWorker.registerActivitiesImplementations(populatePOActivities);

                // Worker for Finalize Receipt workflow
                Worker finalizeWorker = factory.newWorker(FINALIZE_TASK_QUEUE);
                finalizeWorker.registerWorkflowImplementationTypes(FinalizeReceiptWorkflowImpl.class);
                finalizeWorker.registerActivitiesImplementations(finalizeReceiptActivities);

                factory.start();
                this.workerFactory = factory;
                workerStarted = true;
                log.info("Temporal workers started successfully on task queues: {} (Populate), {} (Finalize)",
                         POPULATE_TASK_QUEUE, FINALIZE_TASK_QUEUE);
                return;
            } catch (Exception e) {
                log.warn("Failed to start Temporal workers (attempt {}/{}): {}",
                         attempt, retryAttempts, e.getMessage());
                if (attempt < retryAttempts) {
                    try {
                        log.info("Retrying in {} seconds...", retryDelaySeconds);
                        Thread.sleep(retryDelaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Failed to start Temporal workers after {} attempts", retryAttempts);
                    if (failOnError) {
                        throw new RuntimeException("Temporal workers failed to start", e);
                    }
                    log.warn("Application will continue without Temporal workers. Workflow operations will fail.");
                }
            }
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
