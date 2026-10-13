package com.medcore.api.config;

import com.medcore.api.messaging.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.List;

/**
 * Topic provisioning plus consumer error handling:
 * failed records are retried with exponential back-off and then routed to {@code <topic>.DLT}.
 * Poison messages (unparseable JSON, invalid arguments) skip retries and go straight to the DLT.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Bean
    KafkaAdmin.NewTopics medcoreTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : KafkaTopics.ALL) {
            topics.add(TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build());
            topics.add(TopicBuilder.name(topic + KafkaTopics.DLT_SUFFIX).partitions(PARTITIONS).replicas(1).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, MedcoreProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, record.partition()));

        MedcoreProperties.Kafka config = properties.kafka();
        ExponentialBackOff backOff = new ExponentialBackOff(config.retryBackoff().toMillis(), 2.0);
        backOff.setMaxAttempts(config.retryAttempts());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Retry {} for {}-{}@{}: {}", attempt, record.topic(), record.partition(), record.offset(),
                        ex.getMessage()));
        return handler;
    }
}
