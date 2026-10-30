package com.tripsaga.common;

/** Sent by the orchestrator to a provider. action = RESERVE or CANCEL (compensation). */
public record Command(String bookingId, SagaStep step, String action, String details) {}
