package com.tripsaga.common;

/** "Booking X changed": every booking-service instance reloads it and pushes it to its SSE clients. */
public record BookingUpdate(String bookingId) {}
