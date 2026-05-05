package com.wms.po.infrastructure.cache;

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
     * Execute with lock with custom timeouts
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitTime, leaseTime, DEFAULT_TIME_UNIT);
            if (!acquired) {
                throw new RuntimeException("Could not acquire lock: " + lockKey);
            }

            log.debug("Acquired lock: {}", lockKey);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted: " + lockKey, e);
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
