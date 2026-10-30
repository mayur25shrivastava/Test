package com.tripsaga.provider;

import com.tripsaga.common.Command;
import com.tripsaga.common.Reply;
import com.tripsaga.common.SagaStep;
import com.tripsaga.common.Topics;
import com.tripsaga.common.outbox.IdempotencyGuard;
import com.tripsaga.common.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One codebase, run three times (flight / hotel / cab) with PROVIDER_STEP and its own database.
 * Failure simulation (put in the booking notes):
 *   FAIL_<STEP>  -> business rejection (saga compensates)
 *   CRASH_<STEP> -> technical error (retries, then dead-letter topic, then saga timeout)
 */
@Component
public class CommandListener {
    private final ReservationRepository repo;
    private final OutboxService outbox;
    private final IdempotencyGuard guard;
    private final SagaStep step;

    public CommandListener(ReservationRepository repo, OutboxService outbox, IdempotencyGuard guard,
                           @Value("${provider.step}") SagaStep step) {
        this.repo = repo;
        this.outbox = outbox;
        this.guard = guard;
        this.step = step;
    }

    @KafkaListener(topics = "${provider.topic}", groupId = "${spring.application.name}")
    @Transactional
    public void on(Command command, @Header(name = "x-replayed", required = false) byte[] replayed) {
        if (!guard.firstTime(command.bookingId() + ":" + command.action())) return; // duplicate delivery
        boolean crash = replayed == null && command.details() != null
                && command.details().contains("CRASH_" + step.name()); // replayed from DLT = "bug fixed"
        if (crash) {
            throw new IllegalStateException("Simulated crash in " + step); // rolls back, error handler retries
        }
        Reply reply = "CANCEL".equals(command.action()) ? cancel(command) : reserve(command);
        outbox.enqueue(Topics.REPLIES, command.bookingId(), reply);   // same tx as the reservation
    }

    private Reply reserve(Command c) {
        if (c.details() != null && c.details().contains("FAIL_" + step.name())) {
            return new Reply(c.bookingId(), step, "RESERVE", false, "Simulated " + step + " failure");
        }
        repo.save(new Reservation(c.bookingId(), "RESERVED"));
        return new Reply(c.bookingId(), step, "RESERVE", true, null);
    }

    private Reply cancel(Command c) {
        repo.findById(c.bookingId()).ifPresent(r -> r.setStatus("CANCELLED"));
        return new Reply(c.bookingId(), step, "CANCEL", true, null);
    }
}
