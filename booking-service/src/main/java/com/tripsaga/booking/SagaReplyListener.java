package com.tripsaga.booking;

import com.tripsaga.common.Reply;
import com.tripsaga.common.Topics;
import com.tripsaga.common.outbox.IdempotencyGuard;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SagaReplyListener {
    private final SagaOrchestrator orchestrator;
    private final IdempotencyGuard guard;

    public SagaReplyListener(SagaOrchestrator orchestrator, IdempotencyGuard guard) {
        this.orchestrator = orchestrator;
        this.guard = guard;
    }

    // Same group across replicas: partitions are split, so each booking is handled by one instance.
    @KafkaListener(topics = Topics.REPLIES, groupId = "booking-service")
    @Transactional
    public void on(Reply reply) {
        if (!guard.firstTime(reply.bookingId() + ":" + reply.step() + ":" + reply.action())) return;
        orchestrator.onReply(reply);
    }
}
