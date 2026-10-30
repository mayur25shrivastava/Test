package com.tripsaga.booking;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    public record CreateBookingRequest(String destination, Integer budget, String notes) {}

    private final SagaOrchestrator orchestrator;
    private final BookingRepository repo;
    private final BookingEventRepository events;

    public BookingController(SagaOrchestrator orchestrator, BookingRepository repo, BookingEventRepository events) {
        this.orchestrator = orchestrator;
        this.repo = repo;
        this.events = events;
    }

    /** notes can contain FAIL_<STEP> or CRASH_<STEP> to simulate provider problems. */
    @PostMapping
    public ResponseEntity<BookingView> create(@RequestBody CreateBookingRequest req) {
        String details = req.destination() + " budget=" + req.budget() + " " + (req.notes() == null ? "" : req.notes());
        Booking b = orchestrator.start(details);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BookingView.of(b, events.findByBookingIdOrderByIdAsc(b.getId())));
    }

    @GetMapping
    public List<BookingView> list() {
        return repo.findTop20ByOrderByCreatedAtDesc().stream().map(BookingView::summary).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingView> get(@PathVariable String id) {
        return repo.findById(id)
                .map(b -> ResponseEntity.ok(BookingView.of(b, events.findByBookingIdOrderByIdAsc(id))))
                .orElse(ResponseEntity.notFound().build());
    }
}
