package com.swp391.backend.repository;

import com.swp391.backend.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByBooking_BookingId(Long bookingId);
}
