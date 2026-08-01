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
    private final DomainSupportService support;

    public BookingWorkflowService(DomainSupportService support) {
        this.support = support;
    }

    public Map<String, Object> previewCheckout(ApiRequests.PromotionApply request) {
        AppUser customer = previewCustomer(request.customerId());
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        Slot slot = support.getSlot(request.slotId());
        support.validateSlotBookable(slot);
        BigDecimal fieldPrice = support.calculateFieldPrice(slot);
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services(), slot, null);
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, serviceTotal.add(fieldPrice), request.services(), customer);
        BigDecimal membershipDiscount = support.promotionAllowsMembershipStacking(request.promotionCode())
                ? support.calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = support.money(fieldPrice.add(serviceTotal)
                .subtract(promotionDiscount)
                .subtract(membershipDiscount)
                .max(BigDecimal.ZERO));
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
        BookingSource source = support.parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        AppUser customer = request.customerId() == null ? null : support.getUser(request.customerId());
        if (customer != null && !"Customer".equalsIgnoreCase(customer.getRole().getRoleName())) {
            throw support.badRequest("Booking customer must be a customer account");
        }
        if (customer != null && customer.getStatus() == AccountStatus.locked) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is locked");
        }
        if (source == BookingSource.online && customer == null) {
            throw support.badRequest("A customer account is required for online booking");
        }
        if (source == BookingSource.online && !customer.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify customer email before online booking");
        }
        boolean guestWalkIn = source == BookingSource.walk_in && customer == null;
        if (guestWalkIn) {
            support.requireText(request.guestName(), "Walk-in guest name is required");
            support.requireText(request.guestPhone(), "Walk-in guest phone is required");
        }
        Slot slot = support.getSlot(request.slotId());
        support.validateSlotBookable(slot);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setGuestName(guestWalkIn ? support.clean(request.guestName()) : null);
        booking.setGuestPhone(guestWalkIn ? support.clean(request.guestPhone()) : null);
        booking.setGuestEmail(guestWalkIn && !support.isBlank(request.guestEmail()) ? support.clean(request.guestEmail()) : null);
        if (source == BookingSource.walk_in) {
            booking.setStaff(requireCurrentStaff());
        }
        booking.setSlot(slot);
        booking.setBookingCode("BK" + System.currentTimeMillis());
        booking.setBookingSource(source);
        // A walk-in is not proof of payment. It stays pending until Staff
        // records at least the required deposit through the counter-payment flow.
        booking.setStatus(BookingStatus.pending);
        booking.setNote(request.note());

        BigDecimal fieldPrice = support.calculateFieldPrice(slot);
        BigDecimal serviceTotal = support.calculateServiceTotal(request.services(), slot, null);
        BigDecimal promotionDiscount = support.calculatePromotionDiscount(request.promotionCode(), slot, fieldPrice.add(serviceTotal), request.services(), customer);
        BigDecimal membershipDiscount = support.promotionAllowsMembershipStacking(request.promotionCode())
                ? support.calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = support.money(fieldPrice.add(serviceTotal)
                .subtract(promotionDiscount)
                .subtract(membershipDiscount)
                .max(BigDecimal.ZERO));
        BigDecimal deposit = support.calculateDeposit(total);

        booking.setFieldPriceAmount(fieldPrice);
        booking.setServiceTotalAmount(serviceTotal);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(total);
        booking.setDepositAmount(deposit);
        booking.setRemainingAmount(total);
        booking = support.bookingRepository.save(booking);
        support.saveBookingServices(booking, request.services());
        support.saveBookingPromotion(booking, request.promotionCode(), promotionDiscount);
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

    // Included flow of NgocPA UC-30 Cancel booking.
    @Transactional(readOnly = true)
    public Map<String, Object> previewCancellation(Long bookingId) {
        Booking booking = support.getBooking(bookingId);
        if (optionalCurrentUser() != null) requireBookingVisible(booking);
        return support.calculateCancellationPreview(booking);
    }

    public Map<String, Object> updateBookingStatus(Long bookingId, ApiRequests.BookingStatusUpdate request) {
        AppUser requester = currentUser();
        Booking booking = support.getBooking(bookingId);
        BookingStatus nextStatus = support.parseEnum(BookingStatus.class, request.status(), booking.getStatus());
        boolean staff = isStaff(requester);
        if (isCustomer(requester)) {
            requireBookingVisible(booking);
            if (nextStatus != BookingStatus.cancelled) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customers can only cancel their own bookings");
            }
        } else if (!staff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking customer or venue staff can change booking status");
        }
        requireValidTransition(booking.getStatus(), nextStatus);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slotStart = booking.getSlot().getSlotDate().atTime(booking.getSlot().getStartTime());
        if (nextStatus == BookingStatus.checked_in && now.isBefore(slotStart.minusMinutes(30))) {
            throw support.badRequest("Check-in opens 30 minutes before the booked start time");
        }
        if (nextStatus == BookingStatus.checked_in
                && booking.getPaidAmount().compareTo(booking.getDepositAmount()) < 0) {
            throw support.badRequest("The required deposit must be paid before check-in");
        }
        if (nextStatus == BookingStatus.completed && now.isBefore(slotStart)) {
            throw support.badRequest("A booking cannot be completed before its start time");
        }
        if (nextStatus == BookingStatus.completed && booking.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw support.badRequest("The remaining balance must be paid before completion");
        }
        if (nextStatus == BookingStatus.no_show && now.isBefore(slotStart.plusMinutes(15))) {
            throw support.badRequest("No-show can be recorded 15 minutes after the booked start time");
        }
        if (nextStatus == BookingStatus.cancelled && !now.isBefore(slotStart)) {
            throw support.badRequest("A started booking cannot be cancelled; use check-in or no-show instead");
        }
        switch (nextStatus) {
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
            case no_show -> { }
            default -> {
            }
        }
        if (staff) {
            booking.setStaff(requester);
        }
        if (!support.isBlank(request.note())) {
            booking.setNote(request.note());
        }
        booking.setStatus(nextStatus);
        support.reconcilePromotionUsage(booking);
        return bookingDetail(bookingId);
    }

    // UC-29: rescheduling keeps the original booking/payment history while releasing its old slot.
    public Map<String, Object> reschedule(Long bookingId, ApiRequests.BookingReschedule request) {
        Booking booking = support.getBooking(bookingId);
        AppUser requester = currentUser();
        requireBookingVisible(booking);
        boolean staff = isStaff(requester);
        if (!staff && !isCustomer(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking customer or venue staff can reschedule a booking");
        }
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw support.badRequest("Only pending or confirmed bookings can be rescheduled");
        }
        requireBeforeSlotStart(booking, "A started booking cannot be rescheduled");
        Slot newSlot = support.getSlot(request.newSlotId());
        if (booking.getSlot().getSlotId().equals(newSlot.getSlotId())) {
            throw support.badRequest("Please choose a different slot to reschedule");
        }
        support.validateSlotBookable(newSlot);

        List<ApiRequests.ServiceSelection> currentServices = support.bookingServiceItemRepository
                .findByBooking_BookingId(booking.getBookingId()).stream()
                .map(item -> new ApiRequests.ServiceSelection(item.getExtraService().getExtraServiceId(), item.getQuantity()))
                .toList();
        support.calculateServiceTotal(currentServices, newSlot, booking.getBookingId());

        BigDecimal newFieldPrice = support.calculateFieldPrice(newSlot);
        BigDecimal baseAmount = newFieldPrice.add(booking.getServiceTotalAmount());
        BigDecimal promotionDiscount = recalculatePromotionDiscount(booking, newSlot, baseAmount);
        BigDecimal membershipDiscount = appliedPromotionsAllowMembershipStacking(booking)
                ? support.calculateMembershipDiscount(
                    booking.getCustomer(), baseAmount.subtract(promotionDiscount), booking.getBookingSource())
                : BigDecimal.ZERO.setScale(2);
        BigDecimal newTotal = support.money(baseAmount.subtract(promotionDiscount).subtract(membershipDiscount).max(BigDecimal.ZERO));
        BigDecimal remainingAmount = support.money(newTotal.subtract(booking.getPaidAmount()).max(BigDecimal.ZERO));
        BigDecimal refundableAmount = support.remainingRefundEntitlement(
                booking, booking.getPaidAmount().subtract(newTotal));
        booking.setSlot(newSlot);
        booking.setFieldPriceAmount(newFieldPrice);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(newTotal);
        booking.setDepositAmount(support.calculateDeposit(newTotal));
        booking.setRemainingAmount(remainingAmount);
        booking.setRefundableAmount(refundableAmount);
        support.reconcilePromotionUsage(booking);
        if (staff) {
            booking.setStaff(requester);
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
                    || (booking.getCustomer() != null
                    && Objects.equals(support.resolveEligibleMembershipLevel(booking.getCustomer(), slot.getSlotDate()).getMembershipLevelId(),
                    promotion.getApplicableMembershipLevel().getMembershipLevelId()));
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

    private boolean appliedPromotionsAllowMembershipStacking(Booking booking) {
        return support.bookingPromotionRepository.findByBooking_BookingId(booking.getBookingId()).stream()
                .noneMatch(applied -> applied.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        && !applied.getPromotion().isStackable());
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
        if (!isStaff(requester) && !isCustomer(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking customer or venue staff can update add-ons");
        }
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw support.badRequest("Services can only be changed before check-in");
        }
        requireBeforeSlotStart(booking, "Services cannot be changed after the booked start time");

        List<ApiRequests.ServiceSelection> selections = request.services() == null ? List.of() : request.services();
        BigDecimal serviceTotal = support.calculateServiceTotal(selections, booking.getSlot(), bookingId);
        BigDecimal baseAmount = booking.getFieldPriceAmount().add(serviceTotal);
        List<Long> selectedServiceIds = selections.stream().map(ApiRequests.ServiceSelection::serviceId).toList();
        BigDecimal promotionDiscount = recalculatePromotionDiscount(booking, booking.getSlot(), baseAmount, selectedServiceIds);
        BigDecimal membershipDiscount = appliedPromotionsAllowMembershipStacking(booking)
                ? support.calculateMembershipDiscount(
                    booking.getCustomer(), baseAmount.subtract(promotionDiscount), booking.getBookingSource())
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = support.money(baseAmount.subtract(promotionDiscount).subtract(membershipDiscount).max(BigDecimal.ZERO));
        BigDecimal remaining = support.money(total.subtract(booking.getPaidAmount()).max(BigDecimal.ZERO));
        BigDecimal refundable = support.remainingRefundEntitlement(
                booking, booking.getPaidAmount().subtract(total));

        support.bookingServiceItemRepository.deleteByBooking_BookingId(bookingId);
        support.saveBookingServices(booking, selections);
        booking.setServiceTotalAmount(serviceTotal);
        booking.setPromotionDiscountAmount(promotionDiscount);
        booking.setMembershipDiscountAmount(membershipDiscount);
        booking.setTotalAmount(total);
        booking.setDepositAmount(support.calculateDeposit(total));
        booking.setRemainingAmount(remaining);
        booking.setRefundableAmount(refundable);
        support.reconcilePromotionUsage(booking);
        if (isStaff(requester)) booking.setStaff(requester);
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
            case pending -> next == BookingStatus.cancelled;
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

    /**
     * Anonymous visitors receive an anonymous quote. Personalized membership
     * pricing is available only to the signed-in customer or to Venue Staff
     * preparing a walk-in quote for a selected customer.
     */
    private AppUser previewCustomer(Long customerId) {
        if (customerId == null) return null;
        AppUser requester = optionalCurrentUser();
        if (requester == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sign in to use customer-specific pricing");
        }
        AppUser customer = support.getUser(customerId);
        if (!"Customer".equalsIgnoreCase(customer.getRole().getRoleName())) {
            throw support.badRequest("Checkout customer must be a customer account");
        }
        if (!isStaff(requester) && !Objects.equals(requester.getUserId(), customerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only preview your own membership pricing");
        }
        return customer;
    }

    private boolean isOperator(AppUser user) {
        String role = user.getRole().getRoleName();
        return "Staff".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
    }

    private boolean isStaff(AppUser user) {
        return "Staff".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private boolean isCustomer(AppUser user) {
        return "Customer".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private void requireBeforeSlotStart(Booking booking, String message) {
        LocalDateTime slotStart = booking.getSlot().getSlotDate().atTime(booking.getSlot().getStartTime());
        if (!LocalDateTime.now().isBefore(slotStart)) {
            throw support.badRequest(message);
        }
    }

    /**
     * Customer bookings and counter bookings are intentionally separate flows:
     * a customer creates only their own online booking, while venue staff create
     * only walk-ins. This prevents an operational token from starting PayPal
     * checkout on behalf of a customer.
     */
    private void requireBookingCreationRole(AppUser requester, Long customerId, BookingSource source) {
        if (isCustomer(requester)) {
            if (customerId == null) throw support.badRequest("Customer is required for online booking");
            if (!Objects.equals(requester.getUserId(), customerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only create bookings for your own account");
            }
            if (source != BookingSource.online) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customers can only create online bookings");
            }
            return;
        }
        if (!isStaff(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only customers or venue staff can create bookings");
        }
        if (source != BookingSource.walk_in) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Venue staff can only create walk-in bookings");
        }
    }

    private void requireBookingVisible(Booking booking) {
        AppUser requester = currentUser();
        Long ownerId = booking.getCustomer() == null ? null : booking.getCustomer().getUserId();
        if (!isOperator(requester) && !Objects.equals(ownerId, requester.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own bookings");
        }
    }

    private AppUser requireCurrentStaff() {
        AppUser requester = currentUser();
        if (!isStaff(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Venue staff access is required");
        }
        return requester;
    }
}
