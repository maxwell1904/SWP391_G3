package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.ExtraService;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.SlotStatus;
import com.swp391.backend.repository.AppUserRepository;
import com.swp391.backend.repository.BookingRepository;
import com.swp391.backend.repository.ExtraServiceRepository;
import com.swp391.backend.repository.PromotionRepository;
import com.swp391.backend.repository.SlotRepository;
import com.swp391.backend.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BookingPolicyWorkflowServiceTest {
    @Autowired private BookingWorkflowService bookingWorkflowService;
    @Autowired private PaymentWorkflowService paymentWorkflowService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private SlotRepository slotRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ExtraServiceRepository extraServiceRepository;
    @Autowired private DomainSupportService support;
    @Autowired private PromotionReportService promotionReportService;
    @Autowired private PromotionRepository promotionRepository;

    @BeforeEach
    void signInAsAdminForProtectedWorkflowCalls() {
        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
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
    void reschedulingMovesBookingAndReleasesOriginalSlot() {
        Map<String, Object> created = createBooking();
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        Long oldSlotId = ((Number) created.get("slotId")).longValue();
        Slot replacement = availableSlotExcept(oldSlotId);

        Map<String, Object> detail = bookingWorkflowService.reschedule(bookingId,
                new ApiRequests.BookingReschedule(replacement.getSlotId(), "Customer changed plans"));

        assertThat(detail.get("slotId")).isEqualTo(replacement.getSlotId());
        Booking saved = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(saved.getSlot().getSlotId()).isEqualTo(replacement.getSlotId());
        assertThat(bookingRepository.existsBySlotAndStatusIn(slotRepository.findById(oldSlotId).orElseThrow(),
                List.of(BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in))).isFalse();
    }

    @Test
    void reschedulingRepricesBalanceWithoutLosingRecordedPayment() {
        Map<String, Object> created = createBooking();
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        Long oldSlotId = ((Number) created.get("slotId")).longValue();
        BigDecimal originalFieldPrice = (BigDecimal) created.get("fieldPriceAmount");
        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
        Map<String, Object> paidDetail = paymentWorkflowService.capturePayment(new ApiRequests.PaymentCapture(
                bookingId, "deposit", "cash", null, true));
        BigDecimal paidBeforeReschedule = (BigDecimal) paidDetail.get("paidAmount");
        Slot replacement = availableSlotWithDifferentPrice(oldSlotId, originalFieldPrice);

        Map<String, Object> detail = bookingWorkflowService.reschedule(bookingId,
                new ApiRequests.BookingReschedule(replacement.getSlotId(), "Move to a differently priced slot"));

        BigDecimal newTotal = (BigDecimal) detail.get("totalAmount");
        BigDecimal expectedRemaining = newTotal.subtract(paidBeforeReschedule).max(BigDecimal.ZERO).setScale(2);
        BigDecimal expectedRefund = paidBeforeReschedule.subtract(newTotal).max(BigDecimal.ZERO).setScale(2);
        assertThat(detail.get("fieldPriceAmount")).isNotEqualTo(originalFieldPrice);
        assertThat(detail.get("paidAmount")).isEqualTo(paidBeforeReschedule);
        assertThat(detail.get("remainingAmount")).isEqualTo(expectedRemaining);
        assertThat(detail.get("refundableAmount")).isEqualTo(expectedRefund);
    }

    @Test
    void refundRequiresExplicitApprovalThenCompletion() {
        Map<String, Object> created = createBooking();
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
        Map<String, Object> paid = paymentWorkflowService.capturePayment(new ApiRequests.PaymentCapture(bookingId,
                "deposit", "cash", null, true));
        bookingWorkflowService.updateBookingStatus(bookingId, new ApiRequests.BookingStatusUpdate("cancelled", "Test cancellation"));

        authenticate(customer);
        Map<String, Object> refund = paymentWorkflowService.createRefund(new ApiRequests.RefundCreate(
                bookingId, null, null, "Test request"));
        assertThat(refund.get("status")).isEqualTo("requested");

        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
        Map<String, Object> approved = paymentWorkflowService.updateRefundStatus(
                ((Number) refund.get("refundId")).longValue(),
                new ApiRequests.RefundStatusUpdate("approved", "Reviewed"));
        assertThat(approved.get("status")).isEqualTo("approved");
        Map<String, Object> completed = paymentWorkflowService.updateRefundStatus(
                ((Number) refund.get("refundId")).longValue(),
                new ApiRequests.RefundStatusUpdate("completed", "Provider completed"));
        assertThat(completed.get("status")).isEqualTo("completed");
        assertThat(completed.get("paymentMethod")).isEqualTo("cash");
        assertThat(completed.get("providerStatus")).isEqualTo("MANUAL_CASH_REFUND");
        assertThat(String.valueOf(completed.get("transactionCode"))).startsWith("CASH-REFUND-");
        Booking reconciled = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(reconciled.getRefundableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reconciled.getPaidAmount()).isEqualByComparingTo((BigDecimal) paid.get("paidAmount"));
    }

    @Test
    void completedPartialRefundDoesNotReduceTheNextAvailableRefundTwice() {
        Map<String, Object> created = createBooking();
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
        Map<String, Object> paid = paymentWorkflowService.capturePayment(new ApiRequests.PaymentCapture(
                bookingId, "full", "cash", null, true));
        BigDecimal grossPaid = (BigDecimal) paid.get("paidAmount");
        BigDecimal firstAmount = grossPaid.divide(BigDecimal.valueOf(2)).setScale(2, java.math.RoundingMode.HALF_UP);
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setRefundableAmount(grossPaid);
        bookingRepository.save(booking);

        authenticate(customer);
        Map<String, Object> first = paymentWorkflowService.createRefund(new ApiRequests.RefundCreate(
                bookingId, null, firstAmount, "First partial refund"));
        authenticate(userRepository.findByEmail("staff@goalzone.local").orElseThrow());
        paymentWorkflowService.updateRefundStatus(((Number) first.get("refundId")).longValue(),
                new ApiRequests.RefundStatusUpdate("approved", "Approved"));
        paymentWorkflowService.updateRefundStatus(((Number) first.get("refundId")).longValue(),
                new ApiRequests.RefundStatusUpdate("completed", "Cash returned"));

        authenticate(customer);
        Map<String, Object> second = paymentWorkflowService.createRefund(new ApiRequests.RefundCreate(
                bookingId, null, null, "Remaining refund"));

        assertThat(second.get("refundAmount"))
                .isEqualTo(grossPaid.subtract(firstAmount).setScale(2));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getPaidAmount())
                .isEqualByComparingTo(grossPaid);
    }

    @Test
    void promotionIsCappedAtSubtotalAndStackableControlsMembershipDiscount() {
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        support.customerMembershipRepository.findByCustomer_UserId(customer.getUserId()).orElseThrow()
                .getMembershipLevel().setDiscountPercent(BigDecimal.TEN.setScale(2));
        Slot slot = availableSlotExcept(null);

        Map<String, Object> capped = promotionReportService.createPromotion(promotionRequest(
                "CAPTOTAL", new BigDecimal("999"), true, slot));
        authenticate(customer);
        Map<String, Object> cappedPreview = bookingWorkflowService.previewCheckout(new ApiRequests.PromotionApply(
                customer.getUserId(), slot.getSlotId(), (String) capped.get("promotionCode"), "online", List.of()));
        BigDecimal subtotal = ((BigDecimal) cappedPreview.get("fieldPriceAmount"))
                .add((BigDecimal) cappedPreview.get("serviceTotalAmount"));
        assertThat(cappedPreview.get("promotionDiscountAmount")).isEqualTo(subtotal.setScale(2));
        assertThat(cappedPreview.get("membershipDiscountAmount")).isEqualTo(BigDecimal.ZERO.setScale(2));
        assertThat(cappedPreview.get("totalAmount")).isEqualTo(BigDecimal.ZERO.setScale(2));

        authenticate(userRepository.findByEmail("admin@goalzone.local").orElseThrow());
        Map<String, Object> exclusive = promotionReportService.createPromotion(promotionRequest(
                "EXCLUSIVE", BigDecimal.ONE, false, slot));
        authenticate(customer);
        Map<String, Object> exclusivePreview = bookingWorkflowService.previewCheckout(new ApiRequests.PromotionApply(
                customer.getUserId(), slot.getSlotId(), (String) exclusive.get("promotionCode"), "online", List.of()));
        assertThat(exclusivePreview.get("membershipDiscountAmount")).isEqualTo(BigDecimal.ZERO.setScale(2));

        authenticate(userRepository.findByEmail("admin@goalzone.local").orElseThrow());
        Map<String, Object> stackable = promotionReportService.createPromotion(promotionRequest(
                "STACKABLE", BigDecimal.ONE, true, slot));
        authenticate(customer);
        Map<String, Object> stackablePreview = bookingWorkflowService.previewCheckout(new ApiRequests.PromotionApply(
                customer.getUserId(), slot.getSlotId(), (String) stackable.get("promotionCode"), "online", List.of()));
        assertThat((BigDecimal) stackablePreview.get("membershipDiscountAmount")).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void promotionUsageIsReservedWhilePendingAndReleasedOnCancellation() {
        AppUser admin = userRepository.findByEmail("admin@goalzone.local").orElseThrow();
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        AppUser staff = userRepository.findByEmail("staff@goalzone.local").orElseThrow();
        Slot slot = availableSlotExcept(null);

        authenticate(admin);
        Map<String, Object> promotion = promotionReportService.createPromotion(promotionRequest(
                "COUNTCONF", BigDecimal.ONE, true, slot));
        String code = (String) promotion.get("promotionCode");

        authenticate(customer);
        Map<String, Object> created = bookingWorkflowService.createBooking(new ApiRequests.BookingCreate(
                customer.getUserId(), slot.getSlotId(), "online", code, List.of(), "Count on confirmation"));
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        assertThat(promotionRepository.findByPromotionCodeIgnoreCase(code).orElseThrow().getUsedCount()).isEqualTo(1);

        authenticate(staff);
        paymentWorkflowService.capturePayment(new ApiRequests.PaymentCapture(
                bookingId, "deposit", "cash", null, true));
        assertThat(promotionRepository.findByPromotionCodeIgnoreCase(code).orElseThrow().getUsedCount()).isEqualTo(1);

        bookingWorkflowService.updateBookingStatus(bookingId,
                new ApiRequests.BookingStatusUpdate("cancelled", "Release promotion usage"));
        assertThat(promotionRepository.findByPromotionCodeIgnoreCase(code).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void updatingServicesRepricesBookingAndSynchronizesItsInvoice() {
        Map<String, Object> created = createBooking();
        Long bookingId = ((Number) created.get("bookingId")).longValue();
        ExtraService service = extraServiceRepository.findAll().get(0);

        Map<String, Object> detail = bookingWorkflowService.updateBookingServices(bookingId,
                new ApiRequests.BookingServicesUpdate(List.of(
                        new ApiRequests.ServiceSelection(service.getExtraServiceId(), 2)
                )));

        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) detail.get("invoice");
        assertThat(detail.get("serviceTotalAmount")).isEqualTo(service.getUnitPrice().multiply(BigDecimal.valueOf(2)));
        assertThat(invoice).isNotNull();
        assertThat(invoice.get("serviceAmount")).isEqualTo(detail.get("serviceTotalAmount"));
        assertThat(invoice.get("totalAmount")).isEqualTo(detail.get("totalAmount"));
        assertThat(invoice.get("paidAmount")).isEqualTo(detail.get("paidAmount"));
        assertThat(invoice.get("remainingAmount")).isEqualTo(detail.get("remainingAmount"));
    }

    private Map<String, Object> createBooking() {
        AppUser customer = userRepository.findByEmail("customer@goalzone.local").orElseThrow();
        Slot slot = availableSlotExcept(null);
        authenticate(customer);
        Map<String, Object> booking = bookingWorkflowService.createBooking(new ApiRequests.BookingCreate(customer.getUserId(),
                slot.getSlotId(), "online", null, List.of(), "Policy workflow test"));
        return booking;
    }

    private Slot availableSlotExcept(Long excludedId) {
        return slotRepository.findAll().stream()
                .filter(slot -> slot.getStatus() == SlotStatus.available)
                .filter(slot -> !slot.getSlotId().equals(excludedId))
                .filter(slot -> !bookingRepository.existsBySlotAndStatusIn(slot,
                        List.of(BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in)))
                .findFirst().orElseThrow();
    }

    private Slot availableSlotWithDifferentPrice(Long excludedId, BigDecimal originalPrice) {
        return slotRepository.findAll().stream()
                .filter(slot -> !slot.getSlotId().equals(excludedId))
                .filter(slot -> slot.getStatus() == SlotStatus.available)
                .filter(slot -> !bookingRepository.existsBySlotAndStatusIn(slot,
                        List.of(BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in)))
                .filter(slot -> support.findFieldPrice(slot)
                        .filter(price -> price.compareTo(originalPrice) != 0)
                        .isPresent())
                .findFirst().orElseThrow();
    }

    private ApiRequests.PromotionUpsert promotionRequest(
            String code,
            BigDecimal value,
            boolean stackable,
            Slot slot
    ) {
        return new ApiRequests.PromotionUpsert(
                code,
                code + " promotion",
                "Financial policy regression test",
                null,
                "fixed_amount",
                value,
                null,
                null,
                100,
                slot.getSlotDate(),
                slot.getSlotDate(),
                "active",
                null,
                null,
                null,
                "all",
                null,
                null,
                stackable
        );
    }
}
