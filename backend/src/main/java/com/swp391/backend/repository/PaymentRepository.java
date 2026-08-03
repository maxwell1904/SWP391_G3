package com.swp391.backend.repository;

import com.swp391.backend.entity.Payment;
import com.swp391.backend.enums.PaymentMethod;
import com.swp391.backend.enums.PaymentOption;
import com.swp391.backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBooking_BookingIdOrderByPaymentIdDesc(Long bookingId);
    List<Payment> findByBooking_Customer_UserIdOrderByPaymentIdDesc(Long customerId);
    List<Payment> findAllByOrderByPaymentIdDesc();
    Optional<Payment> findFirstByBooking_BookingIdAndPaymentMethodAndPaymentOptionAndStatusOrderByPaymentIdDesc(
            Long bookingId,
            PaymentMethod paymentMethod,
            PaymentOption paymentOption,
            PaymentStatus status
    );
    long countByBooking_BookingIdAndPaymentMethodAndPaymentOption(
            Long bookingId,
            PaymentMethod paymentMethod,
            PaymentOption paymentOption
    );
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    Optional<Payment> findByProviderCaptureId(String providerCaptureId);
}
