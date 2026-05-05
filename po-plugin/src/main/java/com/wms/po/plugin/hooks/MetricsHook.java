package com.wms.po.plugin.hooks;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection hook
 */
@Component
@Slf4j
public class MetricsHook implements LifecycleHook {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return 2; // Run early
    }

    @Override
    public PluginResult onPrePopulate(PopulateRequest request, VariationContext context) {
        String key = generateKey(request);
        startTimes.put(key, System.currentTimeMillis());

        incrementCounter("populate.started.total");
        incrementCounter("populate.started.region." + context.getRegion());
        incrementCounter("populate.started.client." + context.getClient());

        return PluginResult.success();
    }

    @Override
    public void onPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        String key = generateKey(request);
        Long startTime = startTimes.remove(key);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("METRICS: Populate completed in {}ms for {} POs",
                duration, request.getPoKeys().size());

            // In production, send to metrics backend (Prometheus, DataDog, etc.)
        }

        incrementCounter("populate.success.total");
        incrementCounter("populate.success.region." + context.getRegion());
        incrementCounter("populate.success.client." + context.getClient());
    }

    @Override
    public void onError(String error, PopulateRequest request, VariationContext context) {
        String key = generateKey(request);
        startTimes.remove(key);

        incrementCounter("populate.error.total");
        incrementCounter("populate.error.region." + context.getRegion());
        incrementCounter("populate.error.client." + context.getClient());
    }

    @Override
    public void onCancelled(PopulateRequest request, VariationContext context) {
        String key = generateKey(request);
        startTimes.remove(key);

        incrementCounter("populate.cancelled.total");
    }

    private void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    private String generateKey(PopulateRequest request) {
        return request.getStorerKey() + "_" + String.join("_", request.getPoKeys());
    }

    /**
     * Get all metrics (for monitoring endpoint)
     */
    public ConcurrentHashMap<String, AtomicLong> getMetrics() {
        return counters;
    }
}
