package com.tripsaga.common;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MessagingConfig {

    /**
     * Retries a failing message 4 times with exponential backoff (0.5s, 1s, 2s, 4s),
     * then publishes it to "<topic>.DLT" so it does not block the partition.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        var backoff = new ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(5000);
        return new DefaultErrorHandler(recoverer, backoff);
    }
}
