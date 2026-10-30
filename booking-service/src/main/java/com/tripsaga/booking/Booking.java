package com.tripsaga.booking;

import com.tripsaga.common.SagaStep;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private String id = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.IN_PROGRESS;

    @Column(length = 1000)
    private String details;

    /** Steps successfully reserved so far (CSV, in order). Used to drive compensation. */
    private String completedSteps = "";

    private String failureReason;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    protected Booking() {}

    public Booking(String details) {
        this.details = details;
    }

    public List<SagaStep> completed() {
        if (completedSteps == null || completedSteps.isBlank()) return new ArrayList<>();
        return Arrays.stream(completedSteps.split(","))
                .map(SagaStep::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void markCompleted(SagaStep step) {
        List<SagaStep> list = completed();
        if (!list.contains(step)) {
            list.add(step);
            store(list);
        }
    }

    public void markReleased(SagaStep step) {
        List<SagaStep> list = completed();
        list.remove(step);
        store(list);
    }

    private void store(List<SagaStep> list) {
        completedSteps = list.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getDetails() { return details; }
    public String getCompletedSteps() { return completedSteps; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
