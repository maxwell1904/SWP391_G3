package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Payment;
import com.swp391.backend.entity.Refund;
import com.swp391.backend.enums.*;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PaymentWorkflowService {
    private final DemoSupportService support;
    private final BookingWorkflowService bookingWorkflowService;

    public PaymentWorkflowService(DemoSupportService support, BookingWorkflowService bookingWorkflowService) {
        this.support = support;
        this.bookingWorkflowService = bookingWorkflowService;
    }

    public Map<String, Object> capturePayment(ApiRequests.PaymentCapture request) {
        AppUser operator = currentUser();
        if (!isOperator(operator)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff or administrator access is required for counter payments");
        }
        PaymentMethod method = support.parseEnum(PaymentMethod.class, request.paymentMethod(), PaymentMethod.cash);
        if (method != PaymentMethod.cash) {
            throw support.badRequest("Counter payments must be recorded as cash; online payments are completed through the customer PayPal checkout");
        }
        Booking booking = support.getBooking(request.bookingId());
        PaymentOption paymentOption = support.parseEnum(PaymentOption.class, request.paymentOption(), PaymentOption.deposit);
        BigDecimal payableAmount = payableAmount(booking, paymentOption);
        BigDecimal amount = request.amount() == null ? payableAmount : support.money(request.amount());
        if (amount.compareTo(payableAmount) != 0) {
            throw support.badRequest("Payment amount must match the payable amount for " + paymentOption.name());
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentCode("PAY" + System.currentTimeMillis());
        payment.setPaymentOption(paymentOption);
        payment.setPaymentMethod(method);
        payment.setAmount(amount);
        payment.setCreatedBy(operator);
        payment.setStatus(request.success() ? PaymentStatus.paid : PaymentStatus.failed);
        payment.setGatewayMessage(request.success() ? "Cash payment recorded at the venue" : "Cash payment could not be collected at the venue");
        payment.setTransactionCode("CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        if (request.success()) {
            payment.setPaidAt(LocalDateTime.now());
            booking.setPaidAmount(support.money(booking.getPaidAmount().add(amount)));
            booking.setRemainingAmount(support.money(booking.getTotalAmount().subtract(booking.getPaidAmount()).max(BigDecimal.ZERO)));
            if (booking.getStatus() == BookingStatus.pending && booking.getPaidAmount().compareTo(booking.getDepositAmount()) >= 0) {
                booking.setStatus(BookingStatus.confirmed);
                booking.setConfirmedAt(LocalDateTime.now());
            }
            support.notifyUser(booking.getCustomer(), booking, NotificationType.payment, "Payment captured", "Payment was captured for " + booking.getBookingCode() + ".");
        }
        support.paymentRepository.save(payment);
        support.generateInvoice(booking);
        return bookingWorkflowService.bookingDetail(booking.getBookingId());
    }

    private BigDecimal payableAmount(Booking booking, PaymentOption paymentOption) {
        if (booking.getStatus() == BookingStatus.cancelled
                || booking.getStatus() == BookingStatus.rejected
                || booking.getStatus() == BookingStatus.expired
                || booking.getStatus() == BookingStatus.no_show) {
            throw support.badRequest("Payments cannot be captured for a " + booking.getStatus().name() + " booking");
        }

        BigDecimal amount = switch (paymentOption) {
            case deposit -> booking.getDepositAmount().subtract(booking.getPaidAmount());
            case full, remaining -> booking.getRemainingAmount();
        };
        amount = support.money(amount.max(BigDecimal.ZERO));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("This booking has no payable amount for " + paymentOption.name());
        }
        return amount;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> payments() {
        AppUser requester = currentUser();
        List<Payment> payments = isOperator(requester)
                ? support.paymentRepository.findAllByOrderByPaymentIdDesc()
                : support.paymentRepository.findByBooking_Customer_UserIdOrderByPaymentIdDesc(requester.getUserId());
        return payments.stream().map(support::paymentSummary).toList();
    }

    public Map<String, Object> createRefund(ApiRequests.RefundCreate request) {
        Booking booking = support.getBooking(request.bookingId());
        AppUser requester = currentUser();
        boolean operator = isOperator(requester);
        if (!operator && !booking.getCustomer().getUserId().equals(requester.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only request a refund for your own booking");
        }
        if (booking.getRefundableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("This booking has no refundable payment");
        }
        Refund refund = new Refund();
        refund.setBooking(booking);
        if (request.paymentId() != null) {
            refund.setPayment(support.paymentRepository.findById(request.paymentId()).orElseThrow(() -> support.notFound("Payment not found")));
        }
        refund.setRefundCode("RF" + System.currentTimeMillis());
        refund.setRequestedBy(operator && request.requestedById() != null ? support.getUser(request.requestedById()) : requester);
        if (request.processedById() != null) {
            refund.setProcessedBy(support.getUser(request.processedById()));
        }
        BigDecimal alreadyCommitted = support.refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(booking.getBookingId()).stream()
                .filter(existing -> existing.getStatus() != RefundStatus.rejected && existing.getStatus() != RefundStatus.failed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = booking.getRefundableAmount().subtract(alreadyCommitted).max(BigDecimal.ZERO);
        BigDecimal requestedAmount = request.refundAmount() == null ? available : support.money(request.refundAmount());
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0 || requestedAmount.compareTo(available) > 0) {
            throw support.badRequest("Refund amount must be between zero and the remaining refundable amount");
        }
        refund.setRefundAmount(requestedAmount);
        refund.setRefundReason(request.refundReason());
        refund.setRequestedAt(LocalDateTime.now());
        Refund savedRefund = support.refundRepository.save(refund);
        support.notifyUser(booking.getCustomer(), booking, NotificationType.refund, "Refund requested",
                "Your refund request " + savedRefund.getRefundCode() + " is awaiting staff review.");
        // Kept for backwards compatibility with the old demo client: approval is still explicit and auditable.
        if (request.approveNow()) {
            updateRefundStatus(savedRefund.getRefundId(), new ApiRequests.RefundStatusUpdate("completed", request.processedById(), null));
        }
        return support.refundSummary(savedRefund);
    }

    public Map<String, Object> updateRefundStatus(Long refundId, ApiRequests.RefundStatusUpdate request) {
        if (!isOperator(currentUser())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff or administrator access is required");
        }
        Refund refund = support.refundRepository.findById(refundId)
                .orElseThrow(() -> support.notFound("Refund not found"));
        RefundStatus next = support.parseEnum(RefundStatus.class, request.status(), refund.getStatus());
        RefundStatus current = refund.getStatus();
        boolean validTransition = (current == RefundStatus.requested && (next == RefundStatus.approved || next == RefundStatus.rejected))
                || (current == RefundStatus.approved && (next == RefundStatus.processing || next == RefundStatus.completed))
                || (current == RefundStatus.processing && (next == RefundStatus.completed || next == RefundStatus.failed));
        if (!validTransition) {
            throw support.badRequest("Invalid refund transition from " + current + " to " + next);
        }
        if (request.processedById() != null) {
            refund.setProcessedBy(support.getUser(request.processedById()));
        }
        if (!support.isBlank(request.note())) {
            refund.setRefundReason(request.note());
        }
        refund.setStatus(next);
        if (next == RefundStatus.completed || next == RefundStatus.rejected || next == RefundStatus.failed) {
            refund.setProcessedAt(LocalDateTime.now());
        }
        if (next == RefundStatus.completed) {
            // PayPal refunds need a live merchant credential/capture id. This demo records the provider-agnostic
            // completion here; a production adapter should call PayPal's refund endpoint before this transition.
            refund.setTransactionCode("REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            support.invoiceRepository.findByBooking_BookingId(refund.getBooking().getBookingId())
                    .ifPresent(invoice -> invoice.setRefundAmount(refund.getRefundAmount()));
        }
        Refund saved = support.refundRepository.save(refund);
        String title = switch (next) {
            case approved -> "Refund approved";
            case rejected -> "Refund rejected";
            case processing -> "Refund processing";
            case completed -> "Refund completed";
            case failed -> "Refund failed";
            default -> "Refund updated";
        };
        support.notifyUser(saved.getBooking().getCustomer(), saved.getBooking(), NotificationType.refund, title,
                "Refund " + saved.getRefundCode() + " is now " + next.name() + ".");
        return support.refundSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> refunds() {
        return support.refundRepository.findAllByOrderByRefundIdDesc().stream().map(support::refundSummary).toList();
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        return user.getAppUser();
    }

    private boolean isOperator(AppUser user) {
        String role = user.getRole().getRoleName();
        return "Staff".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
    }
}
