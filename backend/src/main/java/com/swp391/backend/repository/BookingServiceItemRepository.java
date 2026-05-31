package com.swp391.backend.repository;

import com.swp391.backend.entity.BookingServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingServiceItemRepository extends JpaRepository<BookingServiceItem, Long> {
    List<BookingServiceItem> findByBooking_BookingId(Long bookingId);
}
