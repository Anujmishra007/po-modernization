package com.wms.po.infrastructure.database;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local context for multi-tenant database routing.
 * Stores current tenant (V0/V2/country code) for database routing.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentCountry = new ThreadLocal<>();
    private static final ThreadLocal<String> currentDbVersion = new ThreadLocal<>();

    public static void setCurrentTenant(String tenant) {
        log.debug("Setting current tenant: {}", tenant);
        currentTenant.set(tenant);
    }

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public static void setCurrentCountry(String country) {
        currentCountry.set(country);
    }

    public static String getCurrentCountry() {
        return currentCountry.get();
    }

    public static void setCurrentDbVersion(String version) {
        currentDbVersion.set(version);
        // Also set as tenant for routing
        setCurrentTenant(version);
    }

    public static String getCurrentDbVersion() {
        return currentDbVersion.get();
    }

    /**
     * Set full context from JWT/request
     */
    public static void setContext(String country, String dbVersion) {
        setCurrentCountry(country);
        setCurrentDbVersion(dbVersion);
    }

    public static void clear() {
        currentTenant.remove();
        currentCountry.remove();
        currentDbVersion.remove();
    }

    /**
     * Execute a runnable with a specific tenant context
     */
    public static void runWithTenant(String tenant, Runnable action) {
        String previousTenant = getCurrentTenant();
        try {
            setCurrentTenant(tenant);
            action.run();
        } finally {
            if (previousTenant != null) {
                setCurrentTenant(previousTenant);
            } else {
                currentTenant.remove();
            }
        }
    }

    /**
     * Execute a supplier with a specific tenant context
     */
    public static <T> T runWithTenant(String tenant, java.util.function.Supplier<T> action) {
        String previousTenant = getCurrentTenant();
        try {
            setCurrentTenant(tenant);
            return action.get();
        } finally {
            if (previousTenant != null) {
                setCurrentTenant(previousTenant);
            } else {
                currentTenant.remove();
            }
        }
    }
}
