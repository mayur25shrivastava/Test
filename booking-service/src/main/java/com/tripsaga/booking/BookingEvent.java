package com.tripsaga.booking;

import jakarta.persistence.*;
import java.time.Instant;

/** One line of a booking's history; shown as the timeline in the UI. */
@Entity
@Table(name = "booking_events", indexes = @Index(name = "idx_booking_events_booking", columnList = "bookingId"))
public class BookingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String bookingId;
    private Instant at = Instant.now();
    private String step;   // FLIGHT / HOTEL / CAB, or null for saga-level events
    private String type;   // STARTED, STEP_RESERVED, STEP_FAILED, STEP_CANCELLED, SAGA_TIMEOUT, SAGA_CONFIRMED, SAGA_CANCELLED
    @Column(length = 500)
    private String message;

    protected BookingEvent() {}

    public BookingEvent(String bookingId, String step, String type, String message) {
        this.bookingId = bookingId;
        this.step = step;
        this.type = type;
        this.message = message;
    }

    public Instant getAt() { return at; }
    public String getStep() { return step; }
    public String getType() { return type; }
    public String getMessage() { return message; }
}
