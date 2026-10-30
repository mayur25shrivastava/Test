package com.tripsaga.booking;

import com.tripsaga.common.BookingUpdate;
import com.tripsaga.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Every instance uses its OWN consumer group (random id), so every instance sees every update
 * and can push it to whichever SSE clients happen to be connected to it. That is what makes SSE
 * work behind a load balancer with several booking-service replicas.
 */
@Component
public class BookingUpdateListener {
    private final SseHub hub;

    public BookingUpdateListener(SseHub hub) {
        this.hub = hub;
    }

    @KafkaListener(topics = Topics.BOOKING_UPDATES,
            groupId = "#{T(java.util.UUID).randomUUID().toString()}",
            properties = {"spring.json.value.default.type=com.tripsaga.common.BookingUpdate",
                          "auto.offset.reset=latest"})
    public void on(BookingUpdate update) {
        hub.publish(update.bookingId());
    }
}
