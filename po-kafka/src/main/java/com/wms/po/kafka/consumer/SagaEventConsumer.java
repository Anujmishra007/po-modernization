package com.wms.po.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.po.kafka.event.CompensationEvent;
import com.wms.po.kafka.event.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for saga coordination events.
 * Handles distributed transaction management and compensation triggers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaEventConsumer {

    private final ObjectMapper objectMapper;
    private final SagaEventHandler sagaEventHandler;

    @KafkaListener(
            topics = "saga-events",
            groupId = "po-saga-coordinator",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeSagaEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            log.debug("Received saga event: key={}, partition={}, offset={}", key, partition, offset);

            SagaEvent event = objectMapper.readValue(message, SagaEvent.class);

            switch (event.getStatus()) {
                case STARTED -> sagaEventHandler.handleSagaStarted(event);
                case STEP_COMPLETED -> sagaEventHandler.handleStepCompleted(event);
                case STEP_FAILED -> sagaEventHandler.handleStepFailed(event);
                case COMPENSATING -> sagaEventHandler.handleCompensating(event);
                case COMPLETED -> sagaEventHandler.handleSagaCompleted(event);
                case FAILED -> sagaEventHandler.handleSagaFailed(event);
                default -> log.warn("Unknown saga status: {}", event.getStatus());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing saga event: {}", e.getMessage(), e);
            // Don't acknowledge - message will be retried
        }
    }

    @KafkaListener(
            topics = "compensation-events",
            groupId = "po-compensation-handler",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCompensationEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack) {

        try {
            log.info("Received compensation event for saga: {}", key);

            CompensationEvent event = objectMapper.readValue(message, CompensationEvent.class);

            switch (event.getStatus()) {
                case PENDING -> sagaEventHandler.executeCompensation(event);
                case COMPLETED -> sagaEventHandler.handleCompensationCompleted(event);
                case FAILED -> sagaEventHandler.handleCompensationFailed(event);
                default -> log.debug("Skipping compensation event with status: {}", event.getStatus());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing compensation event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handler interface for saga events
     */
    public interface SagaEventHandler {
        void handleSagaStarted(SagaEvent event);
        void handleStepCompleted(SagaEvent event);
        void handleStepFailed(SagaEvent event);
        void handleCompensating(SagaEvent event);
        void handleSagaCompleted(SagaEvent event);
        void handleSagaFailed(SagaEvent event);
        void executeCompensation(CompensationEvent event);
        void handleCompensationCompleted(CompensationEvent event);
        void handleCompensationFailed(CompensationEvent event);
    }
}
