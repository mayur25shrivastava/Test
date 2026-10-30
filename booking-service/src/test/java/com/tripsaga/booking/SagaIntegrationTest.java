package com.tripsaga.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tripsaga.common.Command;
import com.tripsaga.common.Reply;
import com.tripsaga.common.SagaStep;
import com.tripsaga.common.Topics;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the orchestrator + outbox + idempotency against real Postgres and Kafka.
 * Providers are faked in-test: FAIL_<STEP> rejects, SILENT_<STEP> never answers.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        // 10s: the first test pays for Kafka consumer-group start-up, so a tiny timeout would cancel a healthy saga
        "saga.timeout-seconds=10", "saga.sweep-ms=500", "outbox.poll-ms=200"})
@Testcontainers
class SagaIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static final List<Command> seen = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class FakeProviders {
        @Autowired
        private KafkaTemplate<String, Object> template;

        @KafkaListener(topics = {"flight.commands", "hotel.commands", "cab.commands"}, groupId = "fake-providers",
                properties = "spring.json.value.default.type=com.tripsaga.common.Command")
        public void on(Command c) {
            seen.add(c);
            boolean reserve = "RESERVE".equals(c.action());
            String details = c.details() == null ? "" : c.details();
            if (reserve && details.contains("SILENT_" + c.step())) return;
            boolean fail = reserve && details.contains("FAIL_" + c.step());
            template.send(Topics.REPLIES, c.bookingId(),
                    new Reply(c.bookingId(), c.step(), c.action(), !fail, fail ? "rejected" : null));
        }
    }

    @Autowired SagaOrchestrator orchestrator;
    @Autowired BookingRepository repo;
    @Autowired KafkaTemplate<String, Object> template;

    private BookingStatus status(String id) {
        return repo.findById(id).orElseThrow().getStatus();
    }

    private void awaitStatus(String id, BookingStatus expected) {
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertThat(status(id)).isEqualTo(expected));
    }

    @Test
    void happyPathConfirmsAfterAllThreeSteps() {
        String id = orchestrator.start("Goa").getId();
        awaitStatus(id, BookingStatus.CONFIRMED);
        assertThat(repo.findById(id).orElseThrow().getCompletedSteps()).isEqualTo("FLIGHT,HOTEL,CAB");
    }

    @Test
    void hotelFailureCompensatesFlight() {
        String id = orchestrator.start("Goa FAIL_HOTEL").getId();
        awaitStatus(id, BookingStatus.CANCELLED);
        Booking b = repo.findById(id).orElseThrow();
        assertThat(b.getFailureReason()).contains("HOTEL");
        assertThat(b.getCompletedSteps()).isEmpty();
        assertThat(seen).anyMatch(c -> c.bookingId().equals(id)
                && c.step() == SagaStep.FLIGHT && "CANCEL".equals(c.action()));
    }

    @Test
    void silentProviderTriggersTimeoutAndCompensation() {
        String id = orchestrator.start("Goa SILENT_HOTEL").getId();
        awaitStatus(id, BookingStatus.CANCELLED);
        assertThat(repo.findById(id).orElseThrow().getFailureReason()).contains("Timed out");
    }

    @Test
    void duplicateReplyDoesNotChangeOutcome() {
        String id = orchestrator.start("Goa").getId();
        awaitStatus(id, BookingStatus.CONFIRMED);
        template.send(Topics.REPLIES, id, new Reply(id, SagaStep.FLIGHT, "RESERVE", true, null));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(status(id)).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(repo.findById(id).orElseThrow().getCompletedSteps()).isEqualTo("FLIGHT,HOTEL,CAB");
        });
    }
}
