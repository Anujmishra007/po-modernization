package com.wms.po.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.po.kafka.event.POEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for legacy system synchronization.
 * Handles dual-write coordination between V0/V2 databases.
 *
 * Error codes:
 * - INT_001 (69000) - Legacy Sync Failed
 * - TRG_003 (69702) - Event Handler Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacySyncConsumer {

    private final ObjectMapper objectMapper;
    private final LegacySyncHandler legacySyncHandler;

    /**
     * Consume legacy sync events for dual-write coordination.
     *
     * Error codes:
     * - INT_001 (69000) - Legacy Sync Failed
     * - TRG_003 (69702) - Event Handler Failed
     */
    @KafkaListener(
            topics = "legacy-sync-events",
            groupId = "po-legacy-sync",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLegacySyncEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment ack) {

        if (message == null || message.isBlank()) {
            log.error("Received null/blank legacy sync event, acknowledging to skip (legacy error 69702)");
            ack.acknowledge();
            return;
        }

        try {
            log.info("Received legacy sync event: key={}, partition={}", key, partition);

            POEvent event = objectMapper.readValue(message, POEvent.class);

            if (event.getEventType() == null || event.getEventType().isBlank()) {
                log.warn("Legacy sync event missing event type, skipping: key={}", key);
                ack.acknowledge();
                return;
            }

            switch (event.getEventType()) {
                case POEvent.LEGACY_SYNC_STARTED ->
                        legacySyncHandler.syncToLegacy(event);

                case POEvent.LEGACY_ROLLBACK_COMPLETED ->
                        legacySyncHandler.handleRollbackCompleted(event);

                case POEvent.RECEIPT_CREATED ->
                        legacySyncHandler.syncReceiptToLegacy(event);

                case POEvent.INVENTORY_ALLOCATED ->
                        legacySyncHandler.syncInventoryToLegacy(event);

                default ->
                        log.debug("Skipping legacy sync for event type: {}", event.getEventType());
            }

            ack.acknowledge();

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to deserialize legacy sync event: key={}, error={} (legacy error 69702)",
                key, e.getMessage());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing legacy sync event: key={}, error={} (legacy error 69000)",
                key, e.getMessage(), e);
            // Don't acknowledge - allow retry, but consider DLQ for persistent failures
        }
    }

    /**
     * Handler interface for legacy sync operations
     */
    public interface LegacySyncHandler {
        void syncToLegacy(POEvent event);
        void syncReceiptToLegacy(POEvent event);
        void syncInventoryToLegacy(POEvent event);
        void handleRollbackCompleted(POEvent event);
    }
}
