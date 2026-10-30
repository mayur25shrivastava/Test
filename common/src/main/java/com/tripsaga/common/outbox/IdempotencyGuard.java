package com.tripsaga.common.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent consumer: the "seen" marker commits or rolls back together with the business change. */
@Component
public class IdempotencyGuard {
    private final ProcessedMessageRepository repo;

    public IdempotencyGuard(ProcessedMessageRepository repo) {
        this.repo = repo;
    }

    /** @return true if this is the first time the message id is seen. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String messageId) {
        if (repo.existsById(messageId)) return false;
        repo.save(new ProcessedMessage(messageId));
        return true;
    }
}
