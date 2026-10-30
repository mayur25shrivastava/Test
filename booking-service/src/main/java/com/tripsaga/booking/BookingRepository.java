package com.tripsaga.booking;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findTop20ByOrderByCreatedAtDesc();

    List<Booking> findByStatusAndUpdatedAtBefore(BookingStatus status, Instant cutoff);
}
