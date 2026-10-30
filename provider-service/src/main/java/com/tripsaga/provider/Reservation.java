package com.tripsaga.provider;

import jakarta.persistence.*;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private String bookingId;
    private String status;

    protected Reservation() {}

    public Reservation(String bookingId, String status) {
        this.bookingId = bookingId;
        this.status = status;
    }

    public String getBookingId() { return bookingId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
