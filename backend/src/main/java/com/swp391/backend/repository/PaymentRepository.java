package com.swp391.backend.repository;

import com.swp391.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBooking_BookingIdOrderByPaymentIdDesc(Long bookingId);
    List<Payment> findAllByOrderByPaymentIdDesc();
}
