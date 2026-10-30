package com.tripsaga.gateway;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Served when the circuit breaker is open or the call times out. */
@RestController
public class FallbackController {
    @RequestMapping("/fallback/bookings")
    public ResponseEntity<Map<String, String>> bookingsFallback() {
        return ResponseEntity.status(503).body(Map.of(
                "error", "booking-service is temporarily unavailable",
                "action", "please retry shortly"));
    }

    @RequestMapping("/fallback/plans")
    public ResponseEntity<Map<String, String>> plansFallback() {
        return ResponseEntity.status(503).body(Map.of(
                "error", "the trip planner is temporarily unavailable",
                "action", "you can still book directly"));
    }
}
