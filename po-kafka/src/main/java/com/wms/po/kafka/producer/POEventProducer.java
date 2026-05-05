package com.wms.po.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.kafka.event.CompensationEvent;
import com.wms.po.kafka.event.POEvent;
import com.wms.po.kafka.event.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for PO lifecycle events.
 * Supports event sourcing, saga coordination, and audit logging.
 *
 * Error codes:
 * - TRG_002 (69701) - Event Publish Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Topic names
    public static final String TOPIC_PO_EVENTS = "po-events";
    public static final String TOPIC_SAGA_EVENTS = "saga-events";
    public static final String TOPIC_COMPENSATION_EVENTS = "compensation-events";
    public static final String TOPIC_LEGACY_SYNC = "legacy-sync-events";
    public static final String TOPIC_AUDIT = "po-audit-events";
    public static final String TOPIC_NOTIFICATIONS = "po-notifications";

    /**
     * Publish a PO event.
     *
     * Error codes:
     * - TRG_002 (69701) - Event Publish Failed
     *
     * @param event PO event to publish
     * @return Future with send result
     * @throws BusinessException if event is null
     */
    public CompletableFuture<SendResult<String, String>> publishPOEvent(POEvent event) {
        if (event == null) {
            log.error("Cannot publish null PO event (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "PO event is required for publishing")
                .withDetail("event", "null");
        }

        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        String key = event.getAggregateId();
        return publish(TOPIC_PO_EVENTS, key, event);
    }

    /**
     * Publish PO created event
     */
    public CompletableFuture<SendResult<String, String>> publishPOCreated(
            String poKey, String storerKey, String facility, String userId,
            String correlationId, Map<String, Object> payload) {

        POEvent event = POEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(POEvent.PO_CREATED)
                .aggregateId(poKey)
                .aggregateType("PO")
                .version(1)
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .source("po-service")
                .userId(userId)
                .facility(facility)
                .storerKey(storerKey)
                .payload(payload)
                .build();

        return publishPOEvent(event);
    }

    /**
     * Publish PO populated event
     */
    public CompletableFuture<SendResult<String, String>> publishPOPopulated(
            String poKey, String receiptKey, String storerKey, String facility,
            String userId, String correlationId, int detailCount) {

        POEvent event = POEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(POEvent.PO_POPULATED)
                .aggregateId(poKey)
                .aggregateType("PO")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .source("po-workflow")
                .userId(userId)
                .facility(facility)
                .storerKey(storerKey)
                .payload(Map.of(
                        "receiptKey", receiptKey,
                        "detailCount", detailCount
                ))
                .build();

        return publishPOEvent(event);
    }

    /**
     * Publish receipt created event
     */
    public CompletableFuture<SendResult<String, String>> publishReceiptCreated(
            String receiptKey, String poKey, String storerKey, String facility,
            String userId, String correlationId) {

        POEvent event = POEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(POEvent.RECEIPT_CREATED)
                .aggregateId(receiptKey)
                .aggregateType("RECEIPT")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .causationId(poKey)
                .source("po-workflow")
                .userId(userId)
                .facility(facility)
                .storerKey(storerKey)
                .payload(Map.of("poKey", poKey))
                .build();

        return publishPOEvent(event);
    }

    /**
     * Publish saga event for distributed transaction coordination.
     *
     * Error codes:
     * - TRG_002 (69701) - Event Publish Failed
     *
     * @param event Saga event to publish
     * @return Future with send result
     * @throws BusinessException if event is null or saga ID is missing
     */
    public CompletableFuture<SendResult<String, String>> publishSagaEvent(SagaEvent event) {
        if (event == null) {
            log.error("Cannot publish null saga event (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Saga event is required for publishing")
                .withDetail("event", "null");
        }

        if (event.getSagaId() == null || event.getSagaId().isBlank()) {
            log.error("Saga event missing saga ID (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Saga ID is required for saga event")
                .withDetail("sagaId", "null or blank");
        }

        String key = event.getSagaId();
        return publish(TOPIC_SAGA_EVENTS, key, event);
    }

    /**
     * Publish saga started event
     */
    public CompletableFuture<SendResult<String, String>> publishSagaStarted(
            String sagaId, String sagaType, int totalSteps,
            String legacyProcedure, String legacyDatabase) {

        SagaEvent event = SagaEvent.builder()
                .sagaId(sagaId)
                .sagaType(sagaType)
                .status(SagaEvent.SagaStatus.STARTED)
                .totalSteps(totalSteps)
                .startedAt(Instant.now())
                .legacyProcedure(legacyProcedure)
                .legacyDatabase(legacyDatabase)
                .build();

        return publishSagaEvent(event);
    }

    /**
     * Publish compensation event for rollback operations.
     *
     * Error codes:
     * - TRG_002 (69701) - Event Publish Failed
     *
     * @param event Compensation event to publish
     * @return Future with send result
     * @throws BusinessException if event is null or saga ID is missing
     */
    public CompletableFuture<SendResult<String, String>> publishCompensationEvent(CompensationEvent event) {
        if (event == null) {
            log.error("Cannot publish null compensation event (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Compensation event is required for publishing")
                .withDetail("event", "null");
        }

        if (event.getSagaId() == null || event.getSagaId().isBlank()) {
            log.error("Compensation event missing saga ID (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Saga ID is required for compensation event")
                .withDetail("sagaId", "null or blank");
        }

        if (event.getCompensationId() == null) {
            event.setCompensationId(UUID.randomUUID().toString());
        }
        if (event.getTriggeredAt() == null) {
            event.setTriggeredAt(Instant.now());
        }

        String key = event.getSagaId();
        return publish(TOPIC_COMPENSATION_EVENTS, key, event);
    }

    /**
     * Publish legacy sync event for dual-write coordination
     */
    public CompletableFuture<SendResult<String, String>> publishLegacySyncEvent(
            String aggregateId, String operation, String legacyDatabase,
            Map<String, Object> syncData) {

        POEvent event = POEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(POEvent.LEGACY_SYNC_STARTED)
                .aggregateId(aggregateId)
                .timestamp(Instant.now())
                .source("legacy-bridge")
                .dbVersion(legacyDatabase)
                .payload(syncData)
                .metadata(Map.of("operation", operation))
                .build();

        return publish(TOPIC_LEGACY_SYNC, aggregateId, event);
    }

    /**
     * Publish audit event
     */
    public CompletableFuture<SendResult<String, String>> publishAuditEvent(
            String action, String aggregateId, String aggregateType,
            String userId, Map<String, Object> details) {

        POEvent event = POEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("AUDIT_" + action)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .timestamp(Instant.now())
                .source("audit-service")
                .userId(userId)
                .payload(details)
                .build();

        return publish(TOPIC_AUDIT, aggregateId, event);
    }

    /**
     * Internal publish method with error handling.
     *
     * Error codes:
     * - INT_011 (69010) - Event Serialization Failed
     * - TRG_002 (69701) - Event Publish Failed
     */
    private <T> CompletableFuture<SendResult<String, String>> publish(String topic, String key, T event) {
        if (topic == null || topic.isBlank()) {
            log.error("Cannot publish to null/blank topic (legacy error 69701)");
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Topic is required for publishing")
                .withDetail("topic", "null or blank");
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            log.debug("Publishing to topic {}: key={}, event={}", topic, key, json);

            return kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event to {}: {} (legacy error 69701)",
                                topic, ex.getMessage());
                        } else {
                            log.debug("Published event to {} partition {} offset {}",
                                    topic, result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {}: {} (legacy error 69701)",
                topic, e.getMessage());
            throw new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
                "Failed to serialize event: " + e.getMessage(), e)
                .withDetail("topic", topic)
                .withDetail("key", key)
                .withDetail("reason", "serialization");
        }
    }
}
