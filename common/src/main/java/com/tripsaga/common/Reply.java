package com.tripsaga.common;

/** Sent by a provider back to the orchestrator. */
public record Reply(String bookingId, SagaStep step, String action, boolean success, String reason) {}
