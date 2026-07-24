package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import com.swp391.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services(), slot, null);
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, serviceTotal.add(fieldPrice), request.services(), customer);
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
        // This endpoint is intentionally public so a visitor can estimate a basket.
        // Do not return a customer's profile merely because a client supplied an id.
        response.put("customerId", customer == null ? null : customer.getUserId());
        response.put("slot", support.slotSummary(slot));
        return response;
    }

    public Map<String, Object> createBooking(ApiRequests.BookingCreate request) {
        AppUser requester = currentUser();
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        requireBookingCreationRole(requester, request.customerId(), source);
        return createBookingInternal(request);
    }

    /** Used only by the local bootstrap seeder; HTTP callers always use createBooking. */
    public Map<String, Object> createSeedBooking(ApiRequests.BookingCreate request) {
        return createBookingInternal(request);
    }

    private Map<String, Object> createBookingInternal(ApiRequests.BookingCreate request) {
        AppUser customer = support.getUser(request.customerId());
        if (customer.isAccountLocked()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is locked");
        }
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        if (source == BookingSource.online && !customer.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify customer email before online booking");
        }
        Slot slot = support.getSlot(request.slotId());
        support.validateSlotBookable(slot);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        if (source == BookingSource.walk_in) {
            booking.setStaff(resolveOperatingStaff(request.staffId()));
        }
        booking.setSlot(slot);
        booking.setBookingCode("BK" + System.currentTimeMillis());
        booking.setBookingSource(source);
        booking.setStatus(source == BookingSource.walk_in ? BookingStatus.confirmed : BookingStatus.pending);
        booking.setNote(request.note());

        BigDecimal fieldPrice = support.calculateFieldPrice(slot);
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services(), slot, null);
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, fieldPrice.add(serviceTotal), request.services(), customer);
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
        AppUser requester = currentUser();
        if (!isOperator(requester)) {
            if (customerId != null && !Objects.equals(customerId, requester.getUserId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own bookings");
            }
            customerId = requester.getUserId();
        }
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
        if (optionalCurrentUser() != null) requireBookingVisible(booking);
        Map<String, Object> detail = new LinkedHashMap<>(support.bookingSummary(booking));
        detail.put("services", support.bookingServiceItemRepository.findByBooking_BookingId(bookingId).stream().map(support::bookingServiceSummary).toList());
        detail.put("payments", support.paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(bookingId).stream().map(support::paymentSummary).toList());
        detail.put("promotions", support.bookingPromotionRepository.findByBooking_BookingId(bookingId).stream().map(support::bookingPromotionSummary).toList());
        detail.put("invoice", support.invoiceRepository.findByBooking_BookingId(bookingId).map(support::invoiceSummary).orElse(null));
        detail.put("refunds", support.refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(bookingId).stream().map(support::refundSummary).toList());
        detail.put("paymentStatus", support.paymentStatus(booking));
        return detail;
    }

    // Backlog owner: NgocPA - UC-32 Preview cancellation fee/refund.
    @Transactional(readOnly = true)
    public Map<String, Object> previewCancellation(Long bookingId) {
        Booking booking = support.getBooking(bookingId);
        if (optionalCurrentUser() != null) requireBookingVisible(booking);
        return support.calculateCancellationPreview(booking);
    }

    public Map<String, Object> updateBookingStatus(Long bookingId, ApiRequests.BookingStatusUpdate request) {
        AppUser requester = optionalCurrentUser();
        Booking booking = support.getBooking(bookingId);
        BookingStatus nextStatus = support.parseEnum(BookingStatus.class, request.status(), booking.getStatus());
        boolean operator = requester == null || isOperator(requester);
        if (!operator) {
            requireBookingVisible(booking);
            if (nextStatus != BookingStatus.cancelled) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customers can only cancel their own bookings");
            }
        }
        requireValidTransition(booking.getStatus(), nextStatus);
        LocalDateTime now = LocalDateTime.now();
        switch (nextStatus) {
            case confirmed -> {
                booking.setConfirmedAt(now);
                support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation, "Booking confirmed", "Booking " + booking.getBookingCode() + " is confirmed.");
            }
            case checked_in -> booking.setCheckedInAt(now);
            case completed -> {
                booking.setCompletedAt(now);
                support.updateMembershipProgress(booking.getCustomer());
                support.generateInvoice(booking);
            }
            case cancelled -> {
                booking.setCancelledAt(now);
                support.previewAndStoreCancellation(booking);
                support.notifyUser(booking.getCustomer(), booking, NotificationType.cancellation, "Booking cancelled", "Booking " + booking.getBookingCode() + " has been cancelled.");
            }
            case rejected -> {
                support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation, "Booking rejected", "Booking " + booking.getBookingCode() + " was rejected by staff.");
            }
            case no_show -> { }
            case expired -> booking.setExpiredAt(now);
            default -> {
            }
        }
        if (operator && requester != null) {
            booking.setStaff(resolveOperatingStaff(request.staffId()));
        }
        if (!support.isBlank(request.note())) {
            booking.setNote(request.note());
        }
        booking.setStatus(nextStatus);
        return bookingDetail(bookingId);
    }

    // UC-31: rescheduling keeps the original booking/payment history while releasing its old slot.
    public Map<String, Object> reschedule(Long bookingId, ApiRequests.BookingReschedule request) {
        Booking booking = support.getBooking(bookingId);
        AppUser requester = currentUser();
        requireBookingVisible(booking);
        boolean operator = isOperator(requester);
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw support.badRequest("Only pending or confirmed bookings can be rescheduled");
        }
        Slot newSlot = support.getSlot(request.newSlotId());
        if (booking.getSlot().getSlotId().equals(newSlot.getSlotId())) {
            throw support.badRequest("Please choose a different slot to reschedule");
        }
        support.validateSlotBookable(newSlot);

        BigDecimal newFieldPrice = support.calculateFieldPrice(newSlot);
        BigDecimal baseAmount = newFieldPrice.add(booking.getServiceTotalAmount());
        BigDecimal promotionDiscount = recalculatePromotionDiscount(booking, newSlot, baseAmount);
        BigDecimal membershipDiscount = support.calculateMembershipDiscount(
                booking.getCustomer(), baseAmount.subtract(promotionDiscount), booking.getBookingSource());
        BigDecimal newTotal = support.money(baseAmount.subtract(promotionDiscount).subtract(membershipDiscount).max(BigDecimal.ZERO));
        BigDecimal remainingAmount = support.money(newTotal.subtract(booking.getPaidAmount()).max(BigDecimal.ZERO));
        BigDecimal refundableAmount = support.money(booking.getPaidAmount().subtract(newTotal).max(BigDecimal.ZERO));
        booking.setSlot(newSlot);
        booking.setFieldPriceAmount(newFieldPrice);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(newTotal);
        booking.setDepositAmount(support.calculateDeposit(newTotal));
        booking.setRemainingAmount(remainingAmount);
        booking.setRefundableAmount(refundableAmount);
        if (operator) {
            booking.setStaff(resolveOperatingStaff(request.staffId()));
        }
        if (!support.isBlank(request.note())) {
            booking.setNote(request.note());
        }
        support.generateInvoice(booking);
        support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation,
                "Booking rescheduled", "Booking " + booking.getBookingCode() + " was moved to "
                        + newSlot.getSlotDate() + " " + newSlot.getStartTime()
                        + (refundableAmount.compareTo(BigDecimal.ZERO) > 0
                        ? ". A refund of " + refundableAmount + " is available because the new slot costs less."
                        : remainingAmount.compareTo(BigDecimal.ZERO) > 0
                        ? ". The remaining balance is " + remainingAmount + "."
                        : "."));
        return bookingDetail(bookingId);
    }

    /** Reprices a booking amendment without consuming a promotion a second time. */
    private BigDecimal recalculatePromotionDiscount(Booking booking, Slot newSlot, BigDecimal baseAmount) {
        List<Long> selectedServiceIds = support.bookingServiceItemRepository.findByBooking_BookingId(booking.getBookingId()).stream()
                .map(item -> item.getExtraService().getExtraServiceId())
                .toList();
        return recalculatePromotionDiscount(booking, newSlot, baseAmount, selectedServiceIds);
    }

    /** Uses the proposed service set so service-specific promotions stay accurate after an edit. */
    private BigDecimal recalculatePromotionDiscount(
            Booking booking,
            Slot slot,
            BigDecimal baseAmount,
            List<Long> selectedServiceIds
    ) {
        List<BookingPromotion> appliedPromotions = support.bookingPromotionRepository.findByBooking_BookingId(booking.getBookingId());
        if (appliedPromotions.isEmpty()) {
            return support.money(booking.getPromotionDiscountAmount().min(baseAmount));
        }

        BigDecimal discountTotal = BigDecimal.ZERO;
        for (BookingPromotion applied : appliedPromotions) {
            Promotion promotion = applied.getPromotion();
            String slotDayType = newSlotDayType(slot);
            boolean membershipEligible = promotion.getApplicableMembershipLevel() == null
                    || Objects.equals(support.resolveEligibleMembershipLevel(booking.getCustomer(), slot.getSlotDate()).getMembershipLevelId(),
                    promotion.getApplicableMembershipLevel().getMembershipLevelId());
            boolean remainsApplicable = promotion.getStatus() == CommonStatus.active
                    && !slot.getSlotDate().isBefore(promotion.getStartDate())
                    && !slot.getSlotDate().isAfter(promotion.getEndDate())
                    && (promotion.getMinBookingAmount() == null || baseAmount.compareTo(promotion.getMinBookingAmount()) >= 0)
                    && (promotion.getApplicableFieldType() == null
                    || Objects.equals(promotion.getApplicableFieldType().getFieldTypeId(), slot.getField().getFieldType().getFieldTypeId()))
                    && (promotion.getApplicableExtraService() == null
                    || selectedServiceIds.contains(promotion.getApplicableExtraService().getExtraServiceId()))
                    && membershipEligible
                    && (support.isBlank(promotion.getApplicableDayType()) || "all".equalsIgnoreCase(promotion.getApplicableDayType())
                    || slotDayType.equalsIgnoreCase(promotion.getApplicableDayType()))
                    && (promotion.getApplicableStartTime() == null
                    || (!slot.getStartTime().isBefore(promotion.getApplicableStartTime())
                    && !slot.getEndTime().isAfter(promotion.getApplicableEndTime())));
            if (!remainsApplicable) {
                applied.setDiscountAmount(BigDecimal.ZERO.setScale(2));
                continue;
            }

            BigDecimal discount = promotion.getDiscountType() == DiscountType.percent
                    ? baseAmount.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                    : promotion.getDiscountValue();
            if (promotion.getMaxDiscountAmount() != null) {
                discount = discount.min(promotion.getMaxDiscountAmount());
            }
            discount = support.money(discount.min(baseAmount.subtract(discountTotal).max(BigDecimal.ZERO)));
            applied.setDiscountAmount(discount);
            discountTotal = discountTotal.add(discount);
        }
        return support.money(discountTotal);
    }

    private String newSlotDayType(Slot slot) {
        return switch (slot.getSlotDate().getDayOfWeek()) {
            case SATURDAY, SUNDAY -> "weekend";
            default -> "weekday";
        };
    }

    /** UC-21: services can change until the customer has checked in. */
    public Map<String, Object> updateBookingServices(Long bookingId, ApiRequests.BookingServicesUpdate request) {
        AppUser requester = currentUser();
        Booking booking = support.getBooking(bookingId);
        requireBookingVisible(booking);
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw support.badRequest("Services can only be changed before check-in");
        }

        List<ApiRequests.ServiceSelection> selections = request.services() == null ? List.of() : request.services();
        BigDecimal serviceTotal = support.calculateServiceTotal(selections, booking.getSlot(), bookingId);
        BigDecimal baseAmount = booking.getFieldPriceAmount().add(serviceTotal);
        List<Long> selectedServiceIds = selections.stream().map(ApiRequests.ServiceSelection::serviceId).toList();
        BigDecimal promotionDiscount = recalculatePromotionDiscount(booking, booking.getSlot(), baseAmount, selectedServiceIds);
        BigDecimal membershipDiscount = support.calculateMembershipDiscount(
                booking.getCustomer(), baseAmount.subtract(promotionDiscount), booking.getBookingSource());
        BigDecimal total = support.money(baseAmount.subtract(promotionDiscount).subtract(membershipDiscount).max(BigDecimal.ZERO));
        BigDecimal remaining = support.money(total.subtract(booking.getPaidAmount()).max(BigDecimal.ZERO));
        BigDecimal refundable = support.money(booking.getPaidAmount().subtract(total).max(BigDecimal.ZERO));

        support.bookingServiceItemRepository.deleteByBooking_BookingId(bookingId);
        support.saveBookingServices(booking, selections);
        booking.setServiceTotalAmount(serviceTotal);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(total);
        booking.setDepositAmount(support.calculateDeposit(total));
        booking.setRemainingAmount(remaining);
        booking.setRefundableAmount(refundable);
        if (isOperator(requester)) booking.setStaff(resolveOperatingStaff(null));
        support.generateInvoice(booking);
        support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation,
                "Booking services updated", "Services for booking " + booking.getBookingCode() + " were updated before check-in."
                        + (refundable.compareTo(BigDecimal.ZERO) > 0
                        ? " A refund of " + refundable + " is available because the revised total is lower than the amount paid."
                        : remaining.compareTo(BigDecimal.ZERO) > 0
                        ? " The remaining balance is " + remaining + "."
                        : ""));
        return bookingDetail(bookingId);
    }

    private void requireValidTransition(BookingStatus current, BookingStatus next) {
        boolean valid = switch (current) {
            case pending -> List.of(BookingStatus.confirmed, BookingStatus.rejected, BookingStatus.cancelled, BookingStatus.expired).contains(next);
            case confirmed -> List.of(BookingStatus.checked_in, BookingStatus.cancelled, BookingStatus.no_show).contains(next);
            case checked_in -> next == BookingStatus.completed;
            default -> false;
        };
        if (!valid) {
            throw support.badRequest("Booking cannot change from " + current + " to " + next);
        }
    }

    private AppUser currentUser() {
        AppUser user = optionalCurrentUser();
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        return user;
    }

    private AppUser optionalCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            return null;
        }
        return user.getAppUser();
    }

    private boolean isOperator(AppUser user) {
        String role = user.getRole().getRoleName();
        return "Staff".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
    }

    /**
     * Customer bookings and counter bookings are intentionally separate flows:
     * a customer creates only their own online booking, while operators create
     * only walk-ins.  This prevents a staff/admin token from starting PayPal
     * checkout on behalf of a customer.
     */
    private void requireBookingCreationRole(AppUser requester, Long customerId, BookingSource source) {
        if (customerId == null) throw support.badRequest("Customer is required");
        if (!isOperator(requester)) {
            if (!Objects.equals(requester.getUserId(), customerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only create bookings for your own account");
            }
            if (source != BookingSource.online) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customers can only create online bookings");
            }
            return;
        }
        if (source != BookingSource.walk_in) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff and administrators can only create walk-in bookings");
        }
    }

    private void requireBookingVisible(Booking booking) {
        AppUser requester = currentUser();
        if (!isOperator(requester) && !Objects.equals(booking.getCustomer().getUserId(), requester.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own bookings");
        }
    }

    private AppUser resolveOperatingStaff(Long requestedStaffId) {
        AppUser requester = currentUser();
        if ("Staff".equalsIgnoreCase(requester.getRole().getRoleName())) return requester;
        if (requestedStaffId == null) return requester;
        AppUser staff = support.getUser(requestedStaffId);
        if (!"Staff".equalsIgnoreCase(staff.getRole().getRoleName())) {
            throw support.badRequest("The selected operator is not a staff account");
        }
        return staff;
    }
}
