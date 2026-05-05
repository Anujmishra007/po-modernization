package com.wms.po.activity.impl;

import com.wms.po.activity.NotificationActivity;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PopulateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationActivity - best effort notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationActivityImpl implements NotificationActivity {

    // In production, inject:
    // private final KafkaTemplate<String, Object> kafkaTemplate;
    // private final EmailService emailService;
    // private final SlackNotifier slackNotifier;

    @Override
    public void sendPopulationComplete(String receiptKey, PopulateRequest request) {
        log.info("Sending population complete notification: receiptKey={}, poKeys={}",
            receiptKey, request.getPoKeys());

        try {
            // Send Kafka event
            sendKafkaEvent("po.population.complete", new PopulationCompleteEvent(
                receiptKey,
                request.getPoKeys(),
                request.getStorerKey(),
                request.getFacility()
            ));

            // Log for monitoring
            log.info("EVENT: PO_POPULATION_COMPLETE receiptKey={} poKeys={} storer={} facility={}",
                receiptKey, request.getPoKeys(), request.getStorerKey(), request.getFacility());

        } catch (Exception e) {
            // Best effort - don't fail workflow for notification errors
            log.warn("Failed to send population complete notification: {}", e.getMessage());
        }
    }

    @Override
    public void sendPopulationFailed(String receiptKey, String errorMessage, PopulateRequest request) {
        log.warn("Sending population failed notification: receiptKey={}, error={}",
            receiptKey, errorMessage);

        try {
            sendKafkaEvent("po.population.failed", new PopulationFailedEvent(
                receiptKey,
                request.getPoKeys(),
                errorMessage
            ));

            // Alert operations team for failures
            log.error("ALERT: PO_POPULATION_FAILED receiptKey={} poKeys={} error={}",
                receiptKey, request.getPoKeys(), errorMessage);

        } catch (Exception e) {
            log.warn("Failed to send population failed notification: {}", e.getMessage());
        }
    }

    @Override
    public void sendPopulationCancelled(String receiptKey, PopulateRequest request) {
        log.info("Sending population cancelled notification: receiptKey={}", receiptKey);

        try {
            sendKafkaEvent("po.population.cancelled", new PopulationCancelledEvent(
                receiptKey,
                request.getPoKeys()
            ));

        } catch (Exception e) {
            log.warn("Failed to send population cancelled notification: {}", e.getMessage());
        }
    }

    private void sendKafkaEvent(String topic, Object event) {
        // In production: kafkaTemplate.send(topic, event);
        log.debug("Would send Kafka event to topic={}: {}", topic, event);
    }

    // Event classes
    record PopulationCompleteEvent(
        String receiptKey,
        java.util.List<String> poKeys,
        String storerKey,
        String facility
    ) {}

    record PopulationFailedEvent(
        String receiptKey,
        java.util.List<String> poKeys,
        String errorMessage
    ) {}

    record PopulationCancelledEvent(
        String receiptKey,
        java.util.List<String> poKeys
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // Finalization Notifications
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void sendFinalizeComplete(String receiptKey, FinalizeRequest request) {
        log.info("Sending finalization complete notification: receiptKey={}", receiptKey);

        try {
            sendKafkaEvent("receipt.finalize.complete", new FinalizeCompleteEvent(
                receiptKey,
                request.getStorerKey(),
                request.getFacility(),
                request.getUserId()
            ));

            log.info("EVENT: RECEIPT_FINALIZE_COMPLETE receiptKey={} storer={} facility={}",
                receiptKey, request.getStorerKey(), request.getFacility());

        } catch (Exception e) {
            log.warn("Failed to send finalization complete notification: {}", e.getMessage());
        }
    }

    @Override
    public void sendFinalizeFailed(String receiptKey, String errorMessage, FinalizeRequest request) {
        log.warn("Sending finalization failed notification: receiptKey={}, error={}",
            receiptKey, errorMessage);

        try {
            sendKafkaEvent("receipt.finalize.failed", new FinalizeFailedEvent(
                receiptKey,
                errorMessage,
                request.getStorerKey(),
                request.getFacility()
            ));

            log.error("ALERT: RECEIPT_FINALIZE_FAILED receiptKey={} error={}",
                receiptKey, errorMessage);

        } catch (Exception e) {
            log.warn("Failed to send finalization failed notification: {}", e.getMessage());
        }
    }

    @Override
    public void sendFinalizeCancelled(String receiptKey, FinalizeRequest request) {
        log.info("Sending finalization cancelled notification: receiptKey={}", receiptKey);

        try {
            sendKafkaEvent("receipt.finalize.cancelled", new FinalizeCancelledEvent(
                receiptKey,
                request.getStorerKey()
            ));

        } catch (Exception e) {
            log.warn("Failed to send finalization cancelled notification: {}", e.getMessage());
        }
    }

    // Finalization event records
    record FinalizeCompleteEvent(
        String receiptKey,
        String storerKey,
        String facility,
        String userId
    ) {}

    record FinalizeFailedEvent(
        String receiptKey,
        String errorMessage,
        String storerKey,
        String facility
    ) {}

    record FinalizeCancelledEvent(
        String receiptKey,
        String storerKey
    ) {}
}
