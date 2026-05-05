package com.wms.po.infrastructure.cache;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service using Redis/Redisson.
 * Used for saga coordination, inventory allocation, and PO processing.
 *
 * Error codes:
 * - INT_021 (69021) - Deadlock Detected
 * - INT_022 (69022) - Timeout Error
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    // Lock prefixes
    private static final String LOCK_PREFIX = "po-lock:";
    private static final String PO_LOCK_PREFIX = LOCK_PREFIX + "po:";
    private static final String RECEIPT_LOCK_PREFIX = LOCK_PREFIX + "receipt:";
    private static final String INVENTORY_LOCK_PREFIX = LOCK_PREFIX + "inventory:";
    private static final String SAGA_LOCK_PREFIX = LOCK_PREFIX + "saga:";

    // Default timeouts
    private static final long DEFAULT_WAIT_TIME = 30;
    private static final long DEFAULT_LEASE_TIME = 60;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * Execute with lock on a PO
     */
    public <T> T executeWithPOLock(String poKey, Supplier<T> action) {
        return executeWithLock(PO_LOCK_PREFIX + poKey, action);
    }

    /**
     * Execute with lock on a Receipt
     */
    public <T> T executeWithReceiptLock(String receiptKey, Supplier<T> action) {
        return executeWithLock(RECEIPT_LOCK_PREFIX + receiptKey, action);
    }

    /**
     * Execute with lock on inventory location
     */
    public <T> T executeWithInventoryLock(String loc, String lot, Supplier<T> action) {
        String lockKey = INVENTORY_LOCK_PREFIX + loc + ":" + lot;
        return executeWithLock(lockKey, action);
    }

    /**
     * Execute with lock on a saga
     */
    public <T> T executeWithSagaLock(String sagaId, Supplier<T> action) {
        return executeWithLock(SAGA_LOCK_PREFIX + sagaId, action);
    }

    /**
     * Generic lock execution
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, action);
    }

    /**
     * Execute with lock with custom timeouts.
     *
     * Error codes:
     * - INT_022 (69022) - Timeout Error (lock acquisition timeout)
     * - INT_021 (69021) - Deadlock Detected (lock interrupted)
     *
     * @param lockKey Lock key
     * @param waitTime Wait time in seconds
     * @param leaseTime Lease time in seconds
     * @param action Action to execute while holding lock
     * @return Result of the action
     * @throws BusinessException if lock cannot be acquired
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
        if (lockKey == null || lockKey.isBlank()) {
            log.error("Lock key is null/blank (legacy error 69022)");
            throw new BusinessException(ErrorCode.TIMEOUT_ERROR,
                "Lock key is required")
                .withDetail("lockKey", "null or blank");
        }

        if (action == null) {
            log.error("Action is null for lock execution (legacy error 69022)");
            throw new BusinessException(ErrorCode.TIMEOUT_ERROR,
                "Action is required for lock execution")
                .withDetail("lockKey", lockKey)
                .withDetail("action", "null");
        }

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitTime, leaseTime, DEFAULT_TIME_UNIT);
            if (!acquired) {
                log.error("Could not acquire lock: {} within {}s (legacy error 69022)", lockKey, waitTime);
                throw new BusinessException(ErrorCode.TIMEOUT_ERROR,
                    "Could not acquire lock within timeout: " + lockKey)
                    .withDetail("lockKey", lockKey)
                    .withDetail("waitTimeSeconds", waitTime);
            }

            log.debug("Acquired lock: {}", lockKey);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted: {} (legacy error 69021)", lockKey, e);
            throw new BusinessException(ErrorCode.DEADLOCK_DETECTED,
                "Lock acquisition interrupted: " + lockKey, e)
                .withDetail("lockKey", lockKey);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lock execution failed: {} - {} (legacy error 69022)",
                lockKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.TIMEOUT_ERROR,
                "Lock execution failed: " + e.getMessage(), e)
                .withDetail("lockKey", lockKey);

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        }
    }

    /**
     * Try to acquire a lock without blocking
     */
    public boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.tryLock();
    }

    /**
     * Release a lock
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Check if a lock is held
     */
    public boolean isLocked(String lockKey) {
        return redissonClient.getLock(lockKey).isLocked();
    }
}
