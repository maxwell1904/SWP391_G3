package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.SlotStatus;
import com.swp391.backend.repository.AppUserRepository;
import com.swp391.backend.repository.BookingRepository;
import com.swp391.backend.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.paypal.mock-mode=true")
@Transactional
class PayPalCheckoutServiceTest {

    @Autowired
    private BookingWorkflowService bookingWorkflowService;

    @Autowired
    private PayPalCheckoutService payPalCheckoutService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void createsAndCapturesMockPayPalOrder() {
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        Map<String, Object> booking = createBooking(customer);
        Long bookingId = ((Number) booking.get("bookingId")).longValue();

        Map<String, Object> order = payPalCheckoutService.createOrder(
                bookingId,
                new ApiRequests.PayPalOrderCreate(customer.getUserId(), "full")
        );
        Map<String, Object> detail = payPalCheckoutService.captureOrder(
                bookingId,
                (String) order.get("orderId"),
                new ApiRequests.PayPalOrderCapture(customer.getUserId())
        );

        assertThat(order.get("mockMode")).isEqualTo(true);
        assertThat(order.get("paymentOption")).isEqualTo("full");
        assertThat(detail.get("paymentStatus")).isEqualTo("paid");
        assertThat(detail.get("remainingAmount")).isEqualTo(BigDecimal.ZERO.setScale(2));
        assertThat(((Map<?, ?>) detail.get("invoice")).get("invoiceCode")).isNotNull();
        assertThat(firstPayment(detail).get("paymentMethod")).isEqualTo("paypal_sandbox");
        assertThat(firstPayment(detail).get("status")).isEqualTo("paid");
    }

    @Test
    void cancellingPayPalOrderExpiresPendingBookingAndReleasesSlot() {
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        Map<String, Object> booking = createBooking(customer);
        Long bookingId = ((Number) booking.get("bookingId")).longValue();
        Map<String, Object> order = payPalCheckoutService.createOrder(
                bookingId,
                new ApiRequests.PayPalOrderCreate(customer.getUserId(), "deposit")
        );

        Map<String, Object> detail = payPalCheckoutService.cancelOrder(bookingId, (String) order.get("orderId"));

        assertThat(detail.get("status")).isEqualTo("expired");
        assertThat(detail.get("paymentStatus")).isEqualTo("expired");
        assertThat(firstPayment(detail).get("status")).isEqualTo("expired");
    }

    private Map<String, Object> createBooking(AppUser customer) {
        Slot slot = slotRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == SlotStatus.available)
                .filter(candidate -> !bookingRepository.existsBySlotAndStatusIn(
                        candidate,
                        List.of(BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in)
                ))
                .findFirst()
                .orElseThrow();

        return bookingWorkflowService.createBooking(new ApiRequests.BookingCreate(
                customer.getUserId(),
                null,
                slot.getSlotId(),
                "online",
                null,
                List.of(),
                "PayPal integration test"
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstPayment(Map<String, Object> detail) {
        return ((List<Map<String, Object>>) detail.get("payments")).get(0);
    }
}
