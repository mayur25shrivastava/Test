package com.tripsaga.common.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Inbox table: ids of messages already handled by this service. */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {
    @Id
    private String id;
    private Instant processedAt = Instant.now();

    protected ProcessedMessage() {}

    public ProcessedMessage(String id) {
        this.id = id;
    }
}
