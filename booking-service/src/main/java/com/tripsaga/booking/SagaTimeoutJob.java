package com.tripsaga.booking;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safety net: a saga that makes no progress is compensated instead of hanging forever. */
@Component
public class SagaTimeoutJob {
    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutJob.class);

    private final BookingRepository repo;
    private final SagaOrchestrator orchestrator;
    private final long timeoutSeconds;

    public SagaTimeoutJob(BookingRepository repo, SagaOrchestrator orchestrator,
                          @Value("${saga.timeout-seconds:30}") long timeoutSeconds) {
        this.repo = repo;
        this.orchestrator = orchestrator;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${saga.sweep-ms:10000}")
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        for (Booking b : repo.findByStatusAndUpdatedAtBefore(BookingStatus.IN_PROGRESS, cutoff)) {
            try {
                orchestrator.timeout(b.getId());
            } catch (Exception e) { // e.g. optimistic lock: another replica handled it
                log.debug("Timeout handling skipped for {}: {}", b.getId(), e.toString());
            }
        }
    }
}
