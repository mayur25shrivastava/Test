package com.tripsaga.booking;

import java.time.Instant;
import java.util.List;

public record BookingView(String id, BookingStatus status, String details, String completedSteps,
                          String failureReason, Instant createdAt, List<EventView> events) {

    public record EventView(Instant at, String step, String type, String message) {}

    public static BookingView of(Booking b, List<BookingEvent> events) {
        return new BookingView(b.getId(), b.getStatus(), b.getDetails(), b.getCompletedSteps(),
                b.getFailureReason(), b.getCreatedAt(),
                events.stream().map(e -> new EventView(e.getAt(), e.getStep(), e.getType(), e.getMessage())).toList());
    }

    public static BookingView summary(Booking b) {
        return of(b, List.of());
    }
}
