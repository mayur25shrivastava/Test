package com.tripsaga.booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {
    List<BookingEvent> findByBookingIdOrderByIdAsc(String bookingId);
}
