package com.swp391.backend.repository;

import com.swp391.backend.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByBooking_BookingIdOrderByRefundIdDesc(Long bookingId);
    List<Refund> findAllByOrderByRefundIdDesc();
}
