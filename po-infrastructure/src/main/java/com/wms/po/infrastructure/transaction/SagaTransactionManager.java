package com.wms.po.infrastructure.transaction;

import com.wms.po.infrastructure.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Saga transaction manager for distributed transactions.
 * Coordinates transactions across multiple services/databases with compensation support.
 * Maps to legacy stored procedure transaction patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTransactionManager {

    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockService lockService;

    // Track active sagas for recovery
    private final Map<String, SagaState> activeSagas = new ConcurrentHashMap<>();

    /**
     * Execute a saga with automatic compensation on failure
     */
    public <T> T executeSaga(String sagaId, List<SagaStep<T>> steps) {
        SagaState state = new SagaState(sagaId, steps.size());
        activeSagas.put(sagaId, state);

        List<CompensationAction> compensations = new ArrayList<>();
        T result = null;

        try {
            for (int i = 0; i < steps.size(); i++) {
                SagaStep<T> step = steps.get(i);
                state.setCurrentStep(i);
                state.setCurrentStepName(step.getName());

                log.info("Saga [{}] executing step {}/{}: {}",
                        sagaId, i + 1, steps.size(), step.getName());

                // Execute step with optional lock
                if (step.getLockKey() != null) {
                    result = lockService.executeWithLock(step.getLockKey(), step::execute);
                } else {
                    result = step.execute();
                }

                // Register compensation if provided
                if (step.getCompensation() != null) {
                    compensations.add(0, step.getCompensation()); // Add to front for reverse order
                }

                state.completeStep(i);
                log.info("Saga [{}] completed step {}/{}: {}", sagaId, i + 1, steps.size(), step.getName());
            }

            state.setStatus(SagaStatus.COMPLETED);
            log.info("Saga [{}] completed successfully", sagaId);
            return result;

        } catch (Exception e) {
            log.error("Saga [{}] failed at step {}: {}", sagaId, state.getCurrentStepName(), e.getMessage());
            state.setStatus(SagaStatus.COMPENSATING);
            state.setError(e.getMessage());

            // Execute compensations in reverse order
            executeCompensations(sagaId, compensations);

            state.setStatus(SagaStatus.FAILED);
            throw new SagaException("Saga failed: " + e.getMessage(), e, sagaId);

        } finally {
            activeSagas.remove(sagaId);
        }
    }

    /**
     * Execute compensations in reverse order
     */
    private void executeCompensations(String sagaId, List<CompensationAction> compensations) {
        log.info("Saga [{}] executing {} compensations", sagaId, compensations.size());

        for (int i = 0; i < compensations.size(); i++) {
            CompensationAction compensation = compensations.get(i);
            try {
                log.info("Saga [{}] executing compensation {}/{}: {}",
                        sagaId, i + 1, compensations.size(), compensation.getName());

                compensation.execute();

                log.info("Saga [{}] completed compensation: {}", sagaId, compensation.getName());

            } catch (Exception e) {
                log.error("Saga [{}] compensation failed: {} - {}", sagaId, compensation.getName(), e.getMessage());
                // Continue with other compensations even if one fails
            }
        }
    }

    /**
     * Execute a step with database transaction
     */
    public <T> T executeWithTransaction(String stepName, Supplier<T> action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("saga-step-" + stepName);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            T result = action.get();
            transactionManager.commit(status);
            return result;
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    /**
     * Execute a step with savepoint for partial rollback
     */
    public <T> T executeWithSavepoint(String savepointName, Supplier<T> action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        def.setName("savepoint-" + savepointName);

        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            T result = action.get();
            // Don't commit here - let parent transaction handle it
            return result;
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    /**
     * Get saga state (for monitoring/recovery)
     */
    public Optional<SagaState> getSagaState(String sagaId) {
        return Optional.ofNullable(activeSagas.get(sagaId));
    }

    /**
     * Get all active sagas
     */
    public Map<String, SagaState> getActiveSagas() {
        return Collections.unmodifiableMap(activeSagas);
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════

    public enum SagaStatus {
        STARTED, RUNNING, COMPENSATING, COMPLETED, FAILED
    }

    @lombok.Data
    public static class SagaState {
        private final String sagaId;
        private final int totalSteps;
        private SagaStatus status = SagaStatus.STARTED;
        private int currentStep = 0;
        private String currentStepName;
        private Set<Integer> completedSteps = new HashSet<>();
        private String error;
        private long startTime = System.currentTimeMillis();

        public void completeStep(int step) {
            completedSteps.add(step);
        }
    }

    /**
     * Represents a saga step
     */
    @FunctionalInterface
    public interface SagaStepAction<T> {
        T execute();
    }

    @lombok.Data
    @lombok.Builder
    public static class SagaStep<T> {
        private String name;
        private String lockKey;
        private SagaStepAction<T> action;
        private CompensationAction compensation;

        public T execute() {
            return action.execute();
        }
    }

    /**
     * Represents a compensation action
     */
    @lombok.Data
    @lombok.Builder
    public static class CompensationAction {
        private String name;
        private Runnable action;

        public void execute() {
            action.run();
        }
    }

    /**
     * Exception thrown when a saga fails
     */
    public static class SagaException extends RuntimeException {
        private final String sagaId;

        public SagaException(String message, Throwable cause, String sagaId) {
            super(message, cause);
            this.sagaId = sagaId;
        }

        public String getSagaId() {
            return sagaId;
        }
    }
}
