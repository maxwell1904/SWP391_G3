package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class BookingWorkflowService {
    private final DemoSupportService support;

    public BookingWorkflowService(DemoSupportService support) {
        this.support = support;
    }

    public Map<String, Object> previewCheckout(ApiRequests.PromotionApply request) {
        AppUser customer = request.customerId() == null ? null : support.getUser(request.customerId());
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        Slot slot = support.getSlot(request.slotId());
        BigDecimal fieldPrice = support.calculateFieldPrice(slot);
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services());
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, serviceTotal.add(fieldPrice), request.services());
        BigDecimal membershipDiscount = support.calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source);
        BigDecimal total = support.money(fieldPrice.add(serviceTotal).subtract(promotionDiscount).subtract(membershipDiscount));
        BigDecimal deposit = support.calculateDeposit(total);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fieldPriceAmount", fieldPrice);
        response.put("serviceTotalAmount", serviceTotal);
        response.put("promotionDiscountAmount", promotionDiscount);
        response.put("membershipDiscountAmount", membershipDiscount);
        response.put("totalAmount", total);
        response.put("depositAmount", deposit);
        response.put("remainingAfterDeposit", support.money(total.subtract(deposit)));
        response.put("promotionCode", request.promotionCode());
        response.put("bookingSource", source.name());
        response.put("customer", customer == null ? null : support.userSummary(customer));
        response.put("slot", support.slotSummary(slot));
        return response;
    }

    public Map<String, Object> createBooking(ApiRequests.BookingCreate request) {
        AppUser customer = support.getUser(request.customerId());
        if (customer.isBookingRestricted()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer is restricted from creating bookings");
        }
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        if (source == BookingSource.online && !customer.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify customer email before online booking");
        }
        Slot slot = support.getSlot(request.slotId());
        support.validateSlotBookable(slot);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        if (request.staffId() != null) {
            booking.setStaff(support.getUser(request.staffId()));
        }
        booking.setSlot(slot);
        booking.setBookingCode("BK" + System.currentTimeMillis());
        booking.setBookingSource(source);
        booking.setStatus(source == BookingSource.walk_in ? BookingStatus.confirmed : BookingStatus.pending);
        booking.setNote(request.note());

        BigDecimal fieldPrice = support.calculateFieldPrice(slot);
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services());
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, fieldPrice.add(serviceTotal), request.services());
        BigDecimal membershipDiscount = support.calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source);
        BigDecimal total = support.money(fieldPrice.add(serviceTotal).subtract(promotionDiscount).subtract(membershipDiscount));
        BigDecimal deposit = support.calculateDeposit(total);

        booking.setFieldPriceAmount(fieldPrice);
        booking.setServiceTotalAmount(serviceTotal);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(total);
        booking.setDepositAmount(deposit);
        booking.setRemainingAmount(total);
        if (booking.getStatus() == BookingStatus.confirmed) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        booking = support.bookingRepository.save(booking);
        support.saveBookingServices(booking, request.services());
        support.saveBookingPromotion(booking, request.promotionCode(), promotionDiscount);
        support.notifyUser(customer, booking, NotificationType.booking_confirmation, "Booking created", "Your booking " + booking.getBookingCode() + " has been created.");
        return bookingDetail(booking.getBookingId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookings(Long customerId, LocalDate date) {
        List<Booking> source;
        if (customerId != null) {
            source = support.bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(customerId);
        } else if (date != null) {
            source = support.bookingRepository.findBySlot_SlotDateOrderBySlot_StartTimeAsc(date);
        } else {
            source = support.bookingRepository.findAll().stream()
                    .sorted(Comparator.comparing(Booking::getBookingId).reversed())
                    .toList();
        }
        return source.stream().map(support::bookingSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> bookingDetail(Long bookingId) {
        Booking booking = support.getBooking(bookingId);
        Map<String, Object> detail = new LinkedHashMap<>(support.bookingSummary(booking));
        detail.put("services", support.bookingServiceItemRepository.findByBooking_BookingId(bookingId).stream().map(support::bookingServiceSummary).toList());
        detail.put("payments", support.paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(bookingId).stream().map(support::paymentSummary).toList());
        detail.put("promotions", support.bookingPromotionRepository.findByBooking_BookingId(bookingId).stream().map(support::bookingPromotionSummary).toList());
        detail.put("invoice", support.invoiceRepository.findByBooking_BookingId(bookingId).map(support::invoiceSummary).orElse(null));
        detail.put("refunds", support.refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(bookingId).stream().map(support::refundSummary).toList());
        return detail;
    }

    // Backlog owner: NgocPA - UC-32 Preview cancellation fee/refund.
    @Transactional(readOnly = true)
    public Map<String, Object> previewCancellation(Long bookingId) {
        return support.calculateCancellationPreview(support.getBooking(bookingId));
    }

    public Map<String, Object> updateBookingStatus(Long bookingId, ApiRequests.BookingStatusUpdate request) {
        Booking booking = support.getBooking(bookingId);
        BookingStatus nextStatus = support.parseEnum(BookingStatus.class, request.status(), booking.getStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (nextStatus) {
            case confirmed -> {
                booking.setConfirmedAt(now);
                support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation, "Booking confirmed", "Booking " + booking.getBookingCode() + " is confirmed.");
            }
            case checked_in -> {
                support.requireCurrentStatus(booking, BookingStatus.confirmed);
                booking.setCheckedInAt(now);
            }
            case completed -> {
                support.requireCurrentStatus(booking, BookingStatus.checked_in);
                booking.setCompletedAt(now);
                support.updateMembershipProgress(booking.getCustomer());
                support.generateInvoice(booking);
            }
            case cancelled -> {
                if (booking.getStatus() == BookingStatus.checked_in || booking.getStatus() == BookingStatus.completed) {
                    throw support.badRequest("Checked-in or completed bookings cannot be cancelled");
                }
                booking.setCancelledAt(now);
                support.previewAndStoreCancellation(booking);
                support.notifyUser(booking.getCustomer(), booking, NotificationType.cancellation, "Booking cancelled", "Booking " + booking.getBookingCode() + " has been cancelled.");
            }
            case rejected -> {
                support.requireCurrentStatus(booking, BookingStatus.pending);
                support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation, "Booking rejected", "Booking " + booking.getBookingCode() + " was rejected by staff.");
            }
            case no_show -> support.requireCurrentStatus(booking, BookingStatus.confirmed);
            case expired -> booking.setExpiredAt(now);
            default -> {
            }
        }
        if (request.staffId() != null) {
            booking.setStaff(support.getUser(request.staffId()));
        }
        if (!support.isBlank(request.note())) {
            booking.setNote(request.note());
        }
        booking.setStatus(nextStatus);
        return bookingDetail(bookingId);
    }
}
