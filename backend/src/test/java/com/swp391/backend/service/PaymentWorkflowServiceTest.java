package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.SlotStatus;
import com.swp391.backend.repository.AppUserRepository;
import com.swp391.backend.repository.BookingRepository;
import com.swp391.backend.repository.SlotRepository;
import com.swp391.backend.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentWorkflowServiceTest {

    @Autowired
    private BookingWorkflowService bookingWorkflowService;

    @Autowired
    private PaymentWorkflowService paymentWorkflowService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void signInAsCustomer() {
        authenticate(userRepository.findByEmail("customer@goalzone.local").orElseThrow());
    }

    private void authenticate(AppUser user) {
        SecurityUser principal = new SecurityUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void capturesConfiguredDepositWhenAmountIsOmitted() {
        Map<String, Object> booking = createBooking();

        Map<String, Object> detail = capture(booking, "deposit");

        assertThat(detail.get("paidAmount")).isEqualTo(booking.get("depositAmount"));
        assertThat(detail.get("remainingAmount")).isEqualTo(
                ((BigDecimal) booking.get("totalAmount")).subtract((BigDecimal) booking.get("depositAmount"))
        );
        assertThat(detail.get("paymentStatus")).isEqualTo("partially_paid");
        assertThat(firstPayment(detail).get("paymentOption")).isEqualTo("deposit");
    }

    @Test
    void capturesFullRemainingBalanceWhenAmountIsOmitted() {
        Map<String, Object> booking = createBooking();

        Map<String, Object> detail = capture(booking, "full");

        assertThat(detail.get("paidAmount")).isEqualTo(booking.get("totalAmount"));
        assertThat(detail.get("remainingAmount")).isEqualTo(BigDecimal.ZERO.setScale(2));
        assertThat(detail.get("paymentStatus")).isEqualTo("paid");
        assertThat(firstPayment(detail).get("paymentOption")).isEqualTo("full");
    }

    @Test
    void customerPaymentHistoryContainsTheirCapturedPayment() {
        Map<String, Object> booking = createBooking();
        Map<String, Object> detail = capture(booking, "deposit");

        authenticate(userRepository.findByEmail("customer@goalzone.local").orElseThrow());
        List<Map<String, Object>> history = paymentWorkflowService.payments();

        assertThat(history).extracting(payment -> payment.get("paymentCode"))
                .contains(((List<Map<String, Object>>) detail.get("payments")).get(0).get("paymentCode"));
    }

    private Map<String, Object> createBooking() {
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
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
                "Payment option integration test"
        ));
    }

    private Map<String, Object> capture(Map<String, Object> booking, String paymentOption) {
        AppUser staff = userRepository.findByEmail("staff@goalzone.local").orElseThrow();
        authenticate(staff);
        return paymentWorkflowService.capturePayment(new ApiRequests.PaymentCapture(
                ((Number) booking.get("bookingId")).longValue(),
                staff.getUserId(),
                paymentOption,
                "cash",
                null,
                true
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstPayment(Map<String, Object> detail) {
        return ((List<Map<String, Object>>) detail.get("payments")).get(0);
    }
}
