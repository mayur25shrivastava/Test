package com.tripsaga.common.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A message waiting to be published to Kafka. Written in the same DB transaction as the business change. */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_published", columnList = "publishedAt"))
public class OutboxEvent {
    @Id
    private String id = UUID.randomUUID().toString();
    private String topic;
    private String messageKey;
    private String type;
    private String traceparent;   // W3C trace context captured when the event was written
    @Column(columnDefinition = "text")
    private String payload;
    private Instant createdAt = Instant.now();
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String topic, String messageKey, String payload, String type, String traceparent) {
        this.type = type;
        this.traceparent = traceparent;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public String getTraceparent() { return traceparent; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
