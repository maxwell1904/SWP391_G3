package com.swp391.backend.repository;

import com.swp391.backend.entity.BookingPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingPromotionRepository extends JpaRepository<BookingPromotion, Long> {
    List<BookingPromotion> findByBooking_BookingId(Long bookingId);
}
