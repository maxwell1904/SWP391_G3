package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Payment;
import com.swp391.backend.entity.Refund;
import com.swp391.backend.enums.*;
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
        payment.setPaymentMethod(support.parseEnum(PaymentMethod.class, request.paymentMethod(), PaymentMethod.online_sandbox));
        payment.setAmount(amount);
        payment.setCreatedBy(request.createdById() == null ? booking.getCustomer() : support.getUser(request.createdById()));
        payment.setStatus(request.success() ? PaymentStatus.paid : PaymentStatus.failed);
        payment.setGatewayMessage(request.success() ? "Sandbox payment accepted" : "Sandbox payment failed");
        payment.setTransactionCode("SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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
        return support.paymentRepository.findAllByOrderByPaymentIdDesc().stream().map(support::paymentSummary).toList();
    }

    public Map<String, Object> createRefund(ApiRequests.RefundCreate request) {
        Booking booking = support.getBooking(request.bookingId());
        Refund refund = new Refund();
        refund.setBooking(booking);
        if (request.paymentId() != null) {
            refund.setPayment(support.paymentRepository.findById(request.paymentId()).orElseThrow(() -> support.notFound("Payment not found")));
        }
        refund.setRefundCode("RF" + System.currentTimeMillis());
        refund.setRequestedBy(request.requestedById() == null ? booking.getCustomer() : support.getUser(request.requestedById()));
        if (request.processedById() != null) {
            refund.setProcessedBy(support.getUser(request.processedById()));
        }
        refund.setRefundAmount(request.refundAmount() == null ? booking.getRefundableAmount() : support.money(request.refundAmount()));
        refund.setRefundReason(request.refundReason());
        refund.setRequestedAt(LocalDateTime.now());
        if (request.approveNow()) {
            refund.setStatus(RefundStatus.completed);
            refund.setProcessedAt(LocalDateTime.now());
            refund.setTransactionCode("REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            support.notifyUser(booking.getCustomer(), booking, NotificationType.refund, "Refund completed", "Refund has been completed for " + booking.getBookingCode() + ".");
        }
        Refund savedRefund = support.refundRepository.save(refund);
        support.invoiceRepository.findByBooking_BookingId(booking.getBookingId()).ifPresent(invoice -> invoice.setRefundAmount(savedRefund.getRefundAmount()));
        return support.refundSummary(savedRefund);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> refunds() {
        return support.refundRepository.findAllByOrderByRefundIdDesc().stream().map(support::refundSummary).toList();
    }
}
