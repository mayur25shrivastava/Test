package com.tripsaga.booking;

import com.tripsaga.common.BookingUpdate;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import com.tripsaga.common.Command;
import com.tripsaga.common.Reply;
import com.tripsaga.common.SagaStep;
import com.tripsaga.common.Topics;
import com.tripsaga.common.outbox.OutboxService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration-style saga: FLIGHT -> HOTEL -> CAB.
 * On failure (or timeout), completed steps are cancelled in reverse order (compensation).
 * State change, timeline event, outgoing command and UI update are ONE transaction (transactional outbox).
 */
@Service
public class SagaOrchestrator {
    private static final List<SagaStep> FLOW = List.of(SagaStep.FLIGHT, SagaStep.HOTEL, SagaStep.CAB);

    private final BookingRepository repo;
    private final BookingEventRepository events;
    private final OutboxService outbox;
    private final MeterRegistry metrics;

    public SagaOrchestrator(BookingRepository repo, BookingEventRepository events, OutboxService outbox,
                            MeterRegistry metrics) {
        this.repo = repo;
        this.events = events;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public Booking start(String details) {
        Booking booking = repo.save(new Booking(details));
        note(booking, null, "STARTED", "Booking request received");
        send(booking, FLOW.get(0), "RESERVE");
        notifyUpdate(booking);
        return booking;
    }

    @Transactional
    public void onReply(Reply reply) {
        Booking booking = repo.findById(reply.bookingId()).orElse(null);
        if (booking == null) return;

        // Late success (e.g. provider was down, saga already timed out): release the orphan reservation.
        if ("RESERVE".equals(reply.action()) && reply.success()
                && booking.getStatus() != BookingStatus.IN_PROGRESS
                && !booking.completed().contains(reply.step())) {
            note(booking, reply.step(), "STEP_CANCELLED",
                    label(reply.step()) + " answered after the booking ended; releasing it");
            send(booking, reply.step(), "CANCEL");
            notifyUpdate(booking);
            return;
        }
        if (booking.getStatus().isTerminal()) return;

        if ("RESERVE".equals(reply.action())) {
            if (booking.getStatus() != BookingStatus.IN_PROGRESS) return;
            if (reply.success()) {
                if (booking.completed().contains(reply.step())) return;
                booking.markCompleted(reply.step());
                note(booking, reply.step(), "STEP_RESERVED", label(reply.step()) + " reserved");
                int next = FLOW.indexOf(reply.step()) + 1;
                if (next < FLOW.size()) {
                    send(booking, FLOW.get(next), "RESERVE");
                } else {
                    finish(booking, BookingStatus.CONFIRMED);
                    note(booking, null, "SAGA_CONFIRMED", "Trip confirmed");
                }
            } else {
                booking.setStatus(BookingStatus.COMPENSATING);
                booking.setFailureReason(reply.step() + ": " + reply.reason());
                note(booking, reply.step(), "STEP_FAILED", label(reply.step()) + " failed: " + reply.reason());
                metrics.counter("saga.step.failed", "step", reply.step().name()).increment();
                compensateNext(booking);
            }
        } else if ("CANCEL".equals(reply.action())) {
            if (booking.getStatus() != BookingStatus.COMPENSATING) return;
            if (!booking.completed().contains(reply.step())) return;
            booking.markReleased(reply.step());
            note(booking, reply.step(), "STEP_CANCELLED", label(reply.step()) + " released (rolled back)");
            compensateNext(booking);
        }
        repo.save(booking);
        notifyUpdate(booking);
    }

    /** Called by the timeout sweeper when a provider never answered. */
    @Transactional
    public void timeout(String bookingId) {
        Booking booking = repo.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.IN_PROGRESS) return;
        booking.setStatus(BookingStatus.COMPENSATING);
        booking.setFailureReason("Timed out waiting for a provider reply");
        metrics.counter("saga.timeouts").increment();
        note(booking, null, "SAGA_TIMEOUT", "No reply from a provider in time; rolling back");
        compensateNext(booking);
        repo.save(booking);
        notifyUpdate(booking);
    }

    private void compensateNext(Booking booking) {
        List<SagaStep> done = booking.completed();
        if (done.isEmpty()) {
            finish(booking, BookingStatus.CANCELLED);
            note(booking, null, "SAGA_CANCELLED", "Booking cancelled; everything reserved was released");
            return;
        }
        send(booking, done.get(done.size() - 1), "CANCEL"); // reverse order
    }

    private void send(Booking booking, SagaStep step, String action) {
        outbox.enqueue(step.commandTopic(), booking.getId(),
                new Command(booking.getId(), step, action, booking.getDetails()));
    }

    /** Final state: records the outcome and how long the whole saga took. */
    private void finish(Booking booking, BookingStatus status) {
        booking.setStatus(status);
        String outcome = status.name().toLowerCase();
        metrics.counter("saga.completed", "outcome", outcome).increment();
        metrics.timer("saga.duration", "outcome", outcome).record(Duration.between(booking.getCreatedAt(), Instant.now()));
    }

    private void note(Booking booking, SagaStep step, String type, String message) {
        events.save(new BookingEvent(booking.getId(), step == null ? null : step.name(), type, message));
    }

    private void notifyUpdate(Booking booking) {
        outbox.enqueue(Topics.BOOKING_UPDATES, booking.getId(), new BookingUpdate(booking.getId()));
    }

    private static String label(SagaStep step) {
        return step.name().charAt(0) + step.name().substring(1).toLowerCase();
    }
}
