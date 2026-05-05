package com.wms.po.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for PO event streaming.
 * Includes topics, producers, consumers, and error handling.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:po-service}")
    private String groupId;

    // ═══════════════════════════════════════════════════════════════
    // TOPICS
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public NewTopic poEventsTopic() {
        return TopicBuilder.name("po-events")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000)) // 7 days
                .build();
    }

    @Bean
    public NewTopic sagaEventsTopic() {
        return TopicBuilder.name("saga-events")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .build();
    }

    @Bean
    public NewTopic compensationEventsTopic() {
        return TopicBuilder.name("compensation-events")
                .partitions(3)
                .replicas(3)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000L)) // 30 days for audit
                .build();
    }

    @Bean
    public NewTopic legacySyncTopic() {
        return TopicBuilder.name("legacy-sync-events")
                .partitions(6)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic auditTopic() {
        return TopicBuilder.name("po-audit-events")
                .partitions(3)
                .replicas(3)
                .config("retention.ms", String.valueOf(365 * 24 * 60 * 60 * 1000L)) // 1 year
                .build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name("po-notifications")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name("po-events-dlq")
                .partitions(3)
                .replicas(3)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000L)) // 30 days
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // PRODUCER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSUMER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.wms.po.*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Retry 3 times with 1 second interval, then send to DLQ
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate());
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMIN CLIENT
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // ═══════════════════════════════════════════════════════════════
    // OBJECT MAPPER
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
