package com.tripsaga.booking;

public enum BookingStatus {
    IN_PROGRESS, COMPENSATING, CONFIRMED, CANCELLED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
