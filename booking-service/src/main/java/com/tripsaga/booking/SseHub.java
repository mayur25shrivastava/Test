package com.tripsaga.booking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Holds this instance's open SSE connections, grouped by booking id. */
@Component
public class SseHub {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final BookingRepository bookings;
    private final BookingEventRepository events;

    public SseHub(BookingRepository bookings, BookingEventRepository events) {
        this.bookings = bookings;
        this.events = events;
    }

    public SseEmitter subscribe(String bookingId) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        emitters.computeIfAbsent(bookingId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(bookingId, emitter));
        emitter.onTimeout(() -> remove(bookingId, emitter));
        emitter.onError(e -> remove(bookingId, emitter));
        push(bookingId, emitter); // current state right away
        return emitter;
    }

    public void publish(String bookingId) {
        List<SseEmitter> list = emitters.get(bookingId);
        if (list != null) list.forEach(e -> push(bookingId, e));
    }

    private void push(String bookingId, SseEmitter emitter) {
        try {
            Optional<Booking> booking = bookings.findById(bookingId);
            if (booking.isEmpty()) {
                emitter.completeWithError(new IllegalArgumentException("unknown booking"));
                return;
            }
            emitter.send(SseEmitter.event().name("booking")
                    .data(BookingView.of(booking.get(), events.findByBookingIdOrderByIdAsc(bookingId))));
            if (booking.get().getStatus().isTerminal()) {
                emitter.send(SseEmitter.event().name("done").data("done")); // tells the browser not to reconnect
                emitter.complete();
            }
        } catch (Exception e) {
            remove(bookingId, emitter);
        }
    }

    private void remove(String bookingId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(bookingId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(bookingId);
    }

    /** Keeps connections alive through proxies. */
    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        emitters.values().forEach(list -> list.forEach(e -> {
            try { e.send(SseEmitter.event().comment("hb")); } catch (Exception ignored) { /* removed via onError */ }
        }));
    }
}
