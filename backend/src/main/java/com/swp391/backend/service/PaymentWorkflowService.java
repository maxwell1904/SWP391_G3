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
    private final PayPalCheckoutService payPalCheckoutService;

    public PaymentWorkflowService(DemoSupportService support, BookingWorkflowService bookingWorkflowService,
                                  PayPalCheckoutService payPalCheckoutService) {
        this.support = support;
        this.bookingWorkflowService = bookingWorkflowService;
        this.payPalCheckoutService = payPalCheckoutService;
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
        refund.setRefundCode("RF" + System.currentTimeMillis());
        refund.setIdempotencyKey("GZ-REFUND-" + UUID.randomUUID());
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
        refund.setPayment(resolveRefundPayment(booking, request.paymentId(), requestedAmount));
        refund.setRefundReason(request.refundReason());
        refund.setRequestedAt(LocalDateTime.now());
        Refund savedRefund = support.refundRepository.save(refund);
        support.notifyUser(booking.getCustomer(), booking, NotificationType.refund, "Refund requested",
                "Your refund request " + savedRefund.getRefundCode() + " is awaiting staff review.");
        if (request.approveNow()) {
            if (!operator) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only staff or administrators may approve a refund");
            }
            updateRefundStatus(savedRefund.getRefundId(), new ApiRequests.RefundStatusUpdate("approved", request.processedById(), null));
            return updateRefundStatus(savedRefund.getRefundId(), new ApiRequests.RefundStatusUpdate(
                    savedRefund.getPayment().getPaymentMethod() == PaymentMethod.paypal_sandbox ? "processing" : "completed",
                    request.processedById(), null));
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
        boolean providerProcessing = next == RefundStatus.processing
                && (current == RefundStatus.approved || current == RefundStatus.processing || current == RefundStatus.failed);
        boolean validTransition = (current == RefundStatus.requested && (next == RefundStatus.approved || next == RefundStatus.rejected))
                || providerProcessing
                || (current == RefundStatus.approved && next == RefundStatus.completed
                    && refund.getPayment() != null && refund.getPayment().getPaymentMethod() == PaymentMethod.cash);
        if (!validTransition) {
            throw support.badRequest("Invalid refund transition from " + current + " to " + next);
        }
        if (request.processedById() != null) {
            refund.setProcessedBy(support.getUser(request.processedById()));
        }
        if (!support.isBlank(request.note())) {
            refund.setRefundReason(request.note());
        }
        if (providerProcessing) {
            processProviderRefund(refund);
        } else {
            refund.setStatus(next);
        }
        if (refund.getStatus() == RefundStatus.completed || refund.getStatus() == RefundStatus.rejected || refund.getStatus() == RefundStatus.failed) {
            refund.setProcessedAt(LocalDateTime.now());
        }
        if (refund.getStatus() == RefundStatus.completed && refund.getPayment().getPaymentMethod() == PaymentMethod.cash) {
            refund.setProviderStatus("MANUAL_CASH_REFUND");
            refund.setGatewayMessage("Cash refund recorded by venue staff.");
            refund.setTransactionCode("CASH-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        Refund saved = support.refundRepository.save(refund);
        if (saved.getStatus() == RefundStatus.completed) {
            syncBookingAndInvoiceRefundAmounts(saved.getBooking(), saved.getRefundAmount());
        }
        String title = switch (saved.getStatus()) {
            case approved -> "Refund approved";
            case rejected -> "Refund rejected";
            case processing -> "Refund processing";
            case completed -> "Refund completed";
            case failed -> "Refund failed";
            default -> "Refund updated";
        };
        support.notifyUser(saved.getBooking().getCustomer(), saved.getBooking(), NotificationType.refund, title,
                "Refund " + saved.getRefundCode() + " is now " + saved.getStatus().name() + ".");
        return support.refundSummary(saved);
    }

    private void processProviderRefund(Refund refund) {
        Payment payment = refund.getPayment();
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.paypal_sandbox) {
            throw support.badRequest("Only a captured PayPal payment can be sent to the PayPal refund API");
        }
        refund.setStatus(RefundStatus.processing);
        PayPalCheckoutService.RefundResult result = payPalCheckoutService.refundCapture(
                payment, refund.getRefundAmount(), refund.getIdempotencyKey(), refund.getTransactionCode());
        refund.setProviderStatus(result.status());
        refund.setGatewayMessage(result.message());
        if (!support.isBlank(result.refundId())) {
            refund.setTransactionCode(result.refundId());
        }
        refund.setStatus(switch (result.status()) {
            case "COMPLETED" -> RefundStatus.completed;
            case "PENDING" -> RefundStatus.processing;
            default -> RefundStatus.failed;
        });
    }

    private Payment resolveRefundPayment(Booking booking, Long requestedPaymentId, BigDecimal amount) {
        List<Payment> candidates = support.paymentRepository
                .findByBooking_BookingIdOrderByPaymentIdDesc(booking.getBookingId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.paid)
                .toList();
        if (requestedPaymentId != null) {
            Payment selected = candidates.stream()
                    .filter(payment -> payment.getPaymentId().equals(requestedPaymentId))
                    .findFirst()
                    .orElseThrow(() -> support.badRequest("The selected paid payment does not belong to this booking"));
            ensureRefundCapacity(selected, amount);
            return selected;
        }
        return candidates.stream()
                .filter(payment -> payment.getPaymentMethod() == PaymentMethod.paypal_sandbox)
                .filter(payment -> refundableCapacity(payment).compareTo(amount) >= 0)
                .findFirst()
                .or(() -> candidates.stream().filter(payment -> refundableCapacity(payment).compareTo(amount) >= 0).findFirst())
                .orElseThrow(() -> support.badRequest("No single captured payment has enough remaining refundable value"));
    }

    private void ensureRefundCapacity(Payment payment, BigDecimal amount) {
        if (refundableCapacity(payment).compareTo(amount) < 0) {
            throw support.badRequest("Refund amount exceeds the remaining value of the selected payment");
        }
    }

    private BigDecimal refundableCapacity(Payment payment) {
        BigDecimal committed = support.refundRepository
                .findByBooking_BookingIdOrderByRefundIdDesc(payment.getBooking().getBookingId()).stream()
                .filter(refund -> refund.getPayment() != null && refund.getPayment().getPaymentId().equals(payment.getPaymentId()))
                .filter(refund -> refund.getStatus() != RefundStatus.rejected && refund.getStatus() != RefundStatus.failed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.money(payment.getAmount().subtract(committed).max(BigDecimal.ZERO));
    }

    private void syncBookingAndInvoiceRefundAmounts(Booking booking, BigDecimal newlyCompletedAmount) {
        BigDecimal completed = support.refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(booking.getBookingId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.completed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossPaid = support.paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(booking.getBookingId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.paid)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPaid = support.money(grossPaid.subtract(completed).max(BigDecimal.ZERO));
        booking.setPaidAmount(netPaid);
        booking.setRefundableAmount(support.money(booking.getRefundableAmount().subtract(newlyCompletedAmount).max(BigDecimal.ZERO)));
        if (booking.getStatus() != BookingStatus.cancelled) {
            booking.setRemainingAmount(support.money(booking.getTotalAmount().subtract(netPaid).max(BigDecimal.ZERO)));
        }
        support.bookingRepository.save(booking);
        support.invoiceRepository.findByBooking_BookingId(booking.getBookingId()).ifPresent(invoice -> {
            invoice.setRefundAmount(support.money(completed));
            support.invoiceRepository.save(invoice);
        });
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
