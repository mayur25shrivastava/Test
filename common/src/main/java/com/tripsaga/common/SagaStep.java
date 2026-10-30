package com.tripsaga.common;

public enum SagaStep {
    FLIGHT, HOTEL, CAB;

    public String commandTopic() {
        return name().toLowerCase() + ".commands";
    }
}
