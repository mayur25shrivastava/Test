package com.tripsaga.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling publisher: reads unpublished outbox rows and sends them to Kafka (at-least-once).
 * Stops at the first failure to keep order and retries on the next tick.
 * Disabled (outbox.relay.enabled=false) when Debezium CDC publishes the outbox table instead.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;

    public OutboxRelay(OutboxRepository repo, KafkaTemplate<String, Object> kafka, ObjectMapper mapper) {
        this.repo = repo;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    @Transactional
    public void publish() {
        for (OutboxEvent event : repo.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getTopic(), event.getMessageKey(), mapper.readTree(event.getPayload()));
                if (event.getTraceparent() != null) {   // keeps one trace across the async hop
                    record.headers().add("traceparent", event.getTraceparent().getBytes(StandardCharsets.UTF_8));
                }
                kafka.send(record).get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Outbox publish to {} failed, will retry: {}", event.getTopic(), e.toString());
                return;
            }
        }
    }
}
