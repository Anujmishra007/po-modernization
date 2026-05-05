package com.wms.po.plugin.hooks;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Notification hook - sends notifications on workflow events
 */
@Component
@Slf4j
public class NotificationHook implements LifecycleHook {

    // In production, inject:
    // private final KafkaTemplate<String, Object> kafkaTemplate;
    // private final EmailService emailService;
    // private final SlackNotifier slackNotifier;

    @Override
    public int getOrder() {
        return 999; // Run last
    }

    @Override
    public void onPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("NOTIFICATION: Sending populate complete notification for receipt {}", receiptKey);

        try {
            // Send Kafka event
            sendKafkaEvent("po.population.complete", new PopulationEvent(
                receiptKey,
                request.getPoKeys(),
                request.getStorerKey(),
                context.getRegion(),
                "COMPLETED"
            ));

            // Send email to operations team (configurable)
            // sendEmail(request, receiptKey);

        } catch (Exception e) {
            log.warn("Failed to send notification: {}", e.getMessage());
        }
    }

    @Override
    public void onError(String error, PopulateRequest request, VariationContext context) {
        log.warn("NOTIFICATION: Sending error notification");

        try {
            // Send to alert channel
            sendKafkaEvent("po.population.failed", new PopulationEvent(
                null,
                request.getPoKeys(),
                request.getStorerKey(),
                context.getRegion(),
                "FAILED: " + error
            ));

            // Send to Slack/PagerDuty for critical errors
            // sendSlackAlert(error, request);

        } catch (Exception e) {
            log.warn("Failed to send error notification: {}", e.getMessage());
        }
    }

    private void sendKafkaEvent(String topic, Object event) {
        // kafkaTemplate.send(topic, event);
        log.debug("Would send Kafka event to {}: {}", topic, event);
    }

    record PopulationEvent(
        String receiptKey,
        java.util.List<String> poKeys,
        String storerKey,
        String region,
        String status
    ) {}
}
