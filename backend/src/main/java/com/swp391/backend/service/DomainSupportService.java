package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import com.swp391.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class DomainSupportService {
    static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.pending,
            BookingStatus.confirmed,
            BookingStatus.checked_in
    );

    final RoleRepository roleRepository;
    final AppUserRepository userRepository;
    final MembershipLevelRepository membershipLevelRepository;
    final CustomerMembershipRepository customerMembershipRepository;
    final FieldTypeRepository fieldTypeRepository;
    final FootballFieldRepository fieldRepository;
    final FieldPriceRepository fieldPriceRepository;
    final SlotRepository slotRepository;
    final ExtraServiceRepository extraServiceRepository;
    final BookingRepository bookingRepository;
    final BookingServiceItemRepository bookingServiceItemRepository;
    final IssueRepository issueRepository;
    final PromotionRepository promotionRepository;
    final BookingPromotionRepository bookingPromotionRepository;
    final PaymentRepository paymentRepository;
    final InvoiceRepository invoiceRepository;
    final RefundRepository refundRepository;
    final NotificationRepository notificationRepository;
    final SystemSettingRepository systemSettingRepository;

    public DomainSupportService(
            RoleRepository roleRepository,
            AppUserRepository userRepository,
            MembershipLevelRepository membershipLevelRepository,
            CustomerMembershipRepository customerMembershipRepository,
            FieldTypeRepository fieldTypeRepository,
            FootballFieldRepository fieldRepository,
            FieldPriceRepository fieldPriceRepository,
            SlotRepository slotRepository,
            ExtraServiceRepository extraServiceRepository,
            BookingRepository bookingRepository,
            BookingServiceItemRepository bookingServiceItemRepository,
            IssueRepository issueRepository,
            PromotionRepository promotionRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            RefundRepository refundRepository,
            NotificationRepository notificationRepository,
            SystemSettingRepository systemSettingRepository
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.membershipLevelRepository = membershipLevelRepository;
        this.customerMembershipRepository = customerMembershipRepository;
        this.fieldTypeRepository = fieldTypeRepository;
        this.fieldRepository = fieldRepository;
        this.fieldPriceRepository = fieldPriceRepository;
        this.slotRepository = slotRepository;
        this.extraServiceRepository = extraServiceRepository;
        this.bookingRepository = bookingRepository;
        this.bookingServiceItemRepository = bookingServiceItemRepository;
        this.issueRepository = issueRepository;
        this.promotionRepository = promotionRepository;
        this.bookingPromotionRepository = bookingPromotionRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.refundRepository = refundRepository;
        this.notificationRepository = notificationRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    void validateSlotBookable(Slot slot) {
        if (slot.getField().getStatus() != CommonStatus.active) {
            throw badRequest("The field is inactive and cannot be booked");
        }
        if (slot.getStatus() == SlotStatus.blocked) {
            throw badRequest("Slot is blocked: " + nvl(slot.getBlockReason(), "unavailable"));
        }
        if (slot.getSlotDate().isBefore(LocalDate.now())) {
            throw badRequest("Past slots cannot be booked");
        }
        if (slot.getSlotDate().isEqual(LocalDate.now()) && !slot.getStartTime().isAfter(LocalTime.now())) {
            throw badRequest("A slot that has already started cannot be booked");
        }
        if (bookingRepository.existsBySlotAndStatusIn(slot, ACTIVE_BOOKING_STATUSES)) {
            throw conflict("Slot already has an active booking. Please select another time.");
        }
    }

    BigDecimal calculateFieldPrice(Slot slot) {
        String dayType = slot.getSlotDate().getDayOfWeek() == DayOfWeek.SATURDAY || slot.getSlotDate().getDayOfWeek() == DayOfWeek.SUNDAY
                ? "weekend"
                : "weekday";
        return fieldPriceRepository.findByField_FieldId(slot.getField().getFieldId()).stream()
                .filter(price -> price.getStatus() == CommonStatus.active)
                .filter(price -> price.getEffectiveFrom() == null || !slot.getSlotDate().isBefore(price.getEffectiveFrom()))
                .filter(price -> price.getEffectiveTo() == null || !slot.getSlotDate().isAfter(price.getEffectiveTo()))
                .filter(price -> price.getDayType().equalsIgnoreCase(dayType) || price.getDayType().equalsIgnoreCase("all"))
                .filter(price -> !slot.getStartTime().isBefore(price.getStartTime()) && !slot.getEndTime().isAfter(price.getEndTime()))
                .findFirst()
                .map(FieldPrice::getPrice)
                .orElse(BigDecimal.valueOf(12));
    }

    BigDecimal calculateServiceTotal(List<ApiRequests.ServiceSelection> selections, Slot slot, Long excludedBookingId) {
        if (selections == null || selections.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ApiRequests.ServiceSelection selection : selections) {
            ExtraService service = getExtraService(selection.serviceId());
            int quantity = validateServiceQuantity(service, selection.quantity());
            if (service.getStockQuantity() != null) {
                long reserved = bookingServiceItemRepository.sumReservedForOverlappingSlots(
                        service.getExtraServiceId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(),
                        ACTIVE_BOOKING_STATUSES, excludedBookingId);
                if (reserved + quantity > service.getStockQuantity()) {
                    throw badRequest("Service stock is not enough for this time: " + service.getServiceName());
                }
            }
            total = total.add(service.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        return money(total);
    }

    BigDecimal calculatePromotionDiscount(String promotionCode, Slot slot, BigDecimal baseAmount, List<ApiRequests.ServiceSelection> services, AppUser customer) {
        if (isBlank(promotionCode)) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal eligibleBase = money(baseAmount.max(BigDecimal.ZERO));
        Promotion promotion = promotionRepository.findByPromotionCodeIgnoreCase(promotionCode)
                .orElseThrow(() -> badRequest("Promotion not found"));
        LocalDate bookingDate = slot.getSlotDate();
        if (promotion.getStatus() != CommonStatus.active || bookingDate.isBefore(promotion.getStartDate()) || bookingDate.isAfter(promotion.getEndDate())) {
            throw badRequest("Promotion is not active");
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw badRequest("Promotion usage limit reached");
        }
        if (promotion.getMinBookingAmount() != null && eligibleBase.compareTo(promotion.getMinBookingAmount()) < 0) {
            throw badRequest("Booking amount does not meet promotion minimum");
        }
        if (promotion.getApplicableFieldType() != null && !Objects.equals(promotion.getApplicableFieldType().getFieldTypeId(), slot.getField().getFieldType().getFieldTypeId())) {
            throw badRequest("Promotion is not valid for this field type");
        }
        if (promotion.getApplicableExtraService() != null) {
            boolean selected = services != null && services.stream().anyMatch(item -> Objects.equals(item.serviceId(), promotion.getApplicableExtraService().getExtraServiceId()));
            if (!selected) {
                throw badRequest("Promotion requires selected extra service: " + promotion.getApplicableExtraService().getServiceName());
            }
        }
        if (promotion.getApplicableMembershipLevel() != null) {
            boolean eligible = customer != null && Objects.equals(
                    resolveEligibleMembershipLevel(customer, slot.getSlotDate()).getMembershipLevelId(),
                    promotion.getApplicableMembershipLevel().getMembershipLevelId());
            if (!eligible) throw badRequest("Promotion is not valid for this membership level");
        }
        String slotDayType = slot.getSlotDate().getDayOfWeek() == DayOfWeek.SATURDAY || slot.getSlotDate().getDayOfWeek() == DayOfWeek.SUNDAY
                ? "weekend" : "weekday";
        if (!isBlank(promotion.getApplicableDayType())
                && !"all".equalsIgnoreCase(promotion.getApplicableDayType())
                && !slotDayType.equalsIgnoreCase(promotion.getApplicableDayType())) {
            throw badRequest("Promotion is not valid for this day");
        }
        if (promotion.getApplicableStartTime() != null
                && (slot.getStartTime().isBefore(promotion.getApplicableStartTime())
                || slot.getEndTime().isAfter(promotion.getApplicableEndTime()))) {
            throw badRequest("Promotion is not valid for this time range");
        }
        BigDecimal discount = promotion.getDiscountType() == DiscountType.percent
                ? eligibleBase.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        if (promotion.getMaxDiscountAmount() != null) {
            discount = discount.min(promotion.getMaxDiscountAmount());
        }
        return money(discount.max(BigDecimal.ZERO).min(eligibleBase));
    }

    boolean promotionAllowsMembershipStacking(String promotionCode) {
        if (isBlank(promotionCode)) {
            return true;
        }
        return promotionRepository.findByPromotionCodeIgnoreCase(promotionCode)
                .map(Promotion::isStackable)
                .orElse(true);
    }

    BigDecimal calculateMembershipDiscount(AppUser customer, BigDecimal baseAmount, BookingSource source) {
        if (customer == null || source != BookingSource.online) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal eligibleBase = money(baseAmount.max(BigDecimal.ZERO));
        MembershipLevel level = resolveEligibleMembershipLevel(customer, LocalDate.now());
        BigDecimal discount = eligibleBase.multiply(level.getDiscountPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return money(discount.max(BigDecimal.ZERO).min(eligibleBase));
    }

    BigDecimal calculateDeposit(BigDecimal total) {
        BigDecimal percent = systemSettingRepository.findBySettingKey("deposit.default_percent")
                .map(SystemSetting::getSettingValue)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(30));
        return money(total.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    void saveBookingServices(Booking booking, List<ApiRequests.ServiceSelection> selections) {
        if (selections == null) {
            return;
        }
        for (ApiRequests.ServiceSelection selection : selections) {
            ExtraService service = getExtraService(selection.serviceId());
            int quantity = validateServiceQuantity(service, selection.quantity());
            BookingServiceItem item = new BookingServiceItem();
            item.setBooking(booking);
            item.setExtraService(service);
            item.setQuantity(quantity);
            item.setUnitPrice(service.getUnitPrice());
            item.setLineTotal(money(service.getUnitPrice().multiply(BigDecimal.valueOf(quantity))));
            bookingServiceItemRepository.save(item);
        }
    }

    int validateServiceQuantity(ExtraService service, Integer requestedQuantity) {
        if (service.getStatus() != CommonStatus.active) {
            throw badRequest("Service is not active: " + service.getServiceName());
        }
        int quantity = Math.max(1, nvl(requestedQuantity, 1));
        if (service.getMaxQuantityPerBooking() != null && quantity > service.getMaxQuantityPerBooking()) {
            throw badRequest("Quantity exceeds max per booking for " + service.getServiceName());
        }
        if (service.getStockQuantity() != null && quantity > service.getStockQuantity()) {
            throw badRequest("Service stock is not enough for " + service.getServiceName());
        }
        return quantity;
    }

    void saveBookingPromotion(Booking booking, String promotionCode, BigDecimal promotionDiscount) {
        if (isBlank(promotionCode) || promotionDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Promotion promotion = promotionRepository.findByPromotionCodeIgnoreCase(promotionCode).orElseThrow();
        promotion.setUsedCount(promotion.getUsedCount() + 1);
        BookingPromotion bookingPromotion = new BookingPromotion();
        bookingPromotion.setBooking(booking);
        bookingPromotion.setPromotion(promotion);
        bookingPromotion.setPromotionCodeSnapshot(promotion.getPromotionCode());
        bookingPromotion.setDiscountAmount(promotionDiscount);
        bookingPromotionRepository.save(bookingPromotion);
    }

    void generateInvoice(Booking booking) {
        Invoice invoice = invoiceRepository.findByBooking_BookingId(booking.getBookingId()).orElseGet(Invoice::new);
        invoice.setBooking(booking);
        if (invoice.getInvoiceCode() == null) {
            invoice.setInvoiceCode("INV" + System.currentTimeMillis());
        }
        invoice.setFieldAmount(booking.getFieldPriceAmount());
        invoice.setServiceAmount(booking.getServiceTotalAmount());
        invoice.setDiscountAmount(booking.getPromotionDiscountAmount().add(booking.getMembershipDiscountAmount()));
        invoice.setTotalAmount(booking.getTotalAmount());
        invoice.setPaidAmount(booking.getPaidAmount());
        invoice.setRemainingAmount(booking.getRemainingAmount());
        invoice.setIssuedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
    }

    Map<String, Object> calculateCancellationPreview(Booking booking) {
        if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
            throw badRequest("Only pending or confirmed bookings can be cancelled");
        }

        LocalDateTime startAt = LocalDateTime.of(booking.getSlot().getSlotDate(), booking.getSlot().getStartTime());
        long hoursBeforeStart = Duration.between(LocalDateTime.now(), startAt).toHours();
        BigDecimal refundPercent;
        String policy;
        if (hoursBeforeStart >= 24) {
            refundPercent = settingDecimal("refund.before_24h_percent", BigDecimal.valueOf(100));
            policy = "Cancellation at least 24 hours before start";
        } else if (hoursBeforeStart >= 0) {
            refundPercent = settingDecimal("refund.same_day_percent", BigDecimal.valueOf(80));
            policy = "Same-day cancellation before check-in";
        } else {
            refundPercent = BigDecimal.ZERO;
            policy = "Booking start time has passed";
        }

        BigDecimal paid = money(booking.getPaidAmount());
        BigDecimal policyRefund = money(paid.multiply(refundPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal completedRefunds = completedRefundAmount(booking);
        BigDecimal refundable = money(policyRefund.subtract(completedRefunds).max(BigDecimal.ZERO));
        BigDecimal fee = money(paid.subtract(policyRefund).max(BigDecimal.ZERO));
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("bookingId", booking.getBookingId());
        preview.put("bookingCode", booking.getBookingCode());
        preview.put("hoursBeforeStart", hoursBeforeStart);
        preview.put("paidAmount", paid);
        preview.put("refundPercent", refundPercent);
        preview.put("policyRefundAmount", policyRefund);
        preview.put("completedRefundAmount", completedRefunds);
        preview.put("cancellationFeeAmount", fee);
        preview.put("refundableAmount", refundable);
        preview.put("policy", policy);
        return preview;
    }

    void previewAndStoreCancellation(Booking booking) {
        Map<String, Object> preview = calculateCancellationPreview(booking);
        booking.setCancellationFeeAmount((BigDecimal) preview.get("cancellationFeeAmount"));
        booking.setRefundableAmount((BigDecimal) preview.get("refundableAmount"));
    }

    BigDecimal completedRefundAmount(Booking booking) {
        BigDecimal completed = refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(booking.getBookingId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.completed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(completed);
    }

    BigDecimal remainingRefundEntitlement(Booking booking, BigDecimal grossEntitlement) {
        return money(grossEntitlement.max(BigDecimal.ZERO)
                .subtract(completedRefundAmount(booking))
                .max(BigDecimal.ZERO));
    }

    BigDecimal settingDecimal(String key, BigDecimal fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(BigDecimal::new)
                .orElse(fallback);
    }

    void updateMembershipProgress(AppUser customer) {
        CustomerMembership membership = customerMembershipRepository.findByCustomer_UserId(customer.getUserId())
                .orElseGet(() -> attachDefaultMembership(customer));
        long completedCount = bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(customer.getUserId()).stream()
                .filter(booking -> booking.getStatus() == BookingStatus.completed)
                .count();
        membership.setCompletedBookingCount((int) completedCount);
        membership.setMembershipLevel(resolveEligibleMembershipLevel(customer, LocalDate.now()));
        membership.setProgressNote("Updated after completed booking");
    }

    CustomerMembership attachDefaultMembership(AppUser customer) {
        return customerMembershipRepository.findByCustomer_UserId(customer.getUserId())
                .orElseGet(() -> {
                    CustomerMembership membership = new CustomerMembership();
                    membership.setCustomer(customer);
                    membership.setMembershipLevel(defaultMembershipLevel());
                    membership.setEffectiveFrom(LocalDate.now());
                    membership.setProgressNote("Default membership");
                    return customerMembershipRepository.save(membership);
                });
    }

    private MembershipLevel defaultMembershipLevel() {
        return membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> level.getStatus() == CommonStatus.active)
                .findFirst()
                .orElseThrow(() -> serverError("Membership levels have not been seeded"));
    }

    boolean meetsMembershipRequirement(AppUser customer, MembershipLevel level, LocalDate referenceDate) {
        String period = nvl(level.getQualificationPeriod(), "lifetime").toLowerCase(Locale.ROOT);
        if ("weekly".equals(period)) {
            return membershipQualificationProgress(customer, level, referenceDate) >= level.getRequiredConsecutivePeriods();
        }
        return membershipQualificationProgress(customer, level, referenceDate) >= level.getRequiredCompletedBookings();
    }

    MembershipLevel resolveEligibleMembershipLevel(AppUser customer, LocalDate referenceDate) {
        return membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> level.getStatus() == CommonStatus.active)
                .filter(level -> meetsMembershipRequirement(customer, level, referenceDate))
                .reduce((first, second) -> second)
                .orElseGet(this::defaultMembershipLevel);
    }

    void refreshAllMembershipAssignments() {
        userRepository.findAll().stream()
                .filter(user -> "Customer".equalsIgnoreCase(user.getRole().getRoleName()))
                .forEach(this::updateMembershipProgress);
    }

    int membershipQualificationProgress(AppUser customer, MembershipLevel level, LocalDate referenceDate) {
        String period = nvl(level.getQualificationPeriod(), "lifetime").toLowerCase(Locale.ROOT);
        if ("monthly".equals(period)) {
            return completedBookingsInRange(customer, referenceDate.withDayOfMonth(1),
                    referenceDate.plusMonths(1).withDayOfMonth(1).minusDays(1));
        }
        if ("weekly".equals(period)) {
            return qualifyingWeekStreak(customer, level.getRequiredCompletedBookings(), referenceDate);
        }
        return completedBookingsInRange(customer, null, null);
    }

    int completedBookingsInRange(AppUser customer, LocalDate from, LocalDate to) {
        return (int) bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(customer.getUserId()).stream()
                .filter(booking -> booking.getStatus() == BookingStatus.completed)
                .filter(booking -> from == null || !booking.getSlot().getSlotDate().isBefore(from))
                .filter(booking -> to == null || !booking.getSlot().getSlotDate().isAfter(to))
                .count();
    }

    int qualifyingWeekStreak(AppUser customer, int minimumBookings, LocalDate referenceDate) {
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int streak = 0;
        for (int offset = 0; offset < 104; offset++) {
            LocalDate start = weekStart.minusWeeks(offset);
            if (completedBookingsInRange(customer, start, start.plusDays(6)) < minimumBookings) break;
            streak++;
        }
        return streak;
    }

    void notifyUser(AppUser user, Booking booking, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setBooking(booking);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    Map<String, Object> userSummary(AppUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", user.getUserId());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("address", user.getAddress());
        map.put("role", user.getRole().getRoleName());
        map.put("status", user.getStatus().name());
        // Kept for frontend/API compatibility; the persisted source of truth is status.
        map.put("accountLocked", user.getStatus() == AccountStatus.locked);
        map.put("emailVerified", user.isEmailVerified());
        map.put("lockReason", user.getLockReason());
        map.put("lastLoginAt", user.getLastLoginAt());
        return map;
    }

    Map<String, Object> fieldSummary(FootballField field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldId", field.getFieldId());
        map.put("fieldTypeId", field.getFieldType().getFieldTypeId());
        map.put("fieldName", field.getFieldName());
        map.put("fieldType", field.getFieldType().getTypeName());
        map.put("playerCapacity", field.getFieldType().getPlayerCapacity());
        map.put("description", field.getDescription());
        map.put("imageUrl", field.getImageUrl());
        map.put("location", field.getLocation());
        map.put("surfaceType", field.getSurfaceType());
        map.put("status", field.getStatus().name());
        return map;
    }

    Map<String, Object> slotSummary(Slot slot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slotId", slot.getSlotId());
        map.put("fieldId", slot.getField().getFieldId());
        map.put("fieldName", slot.getField().getFieldName());
        map.put("slotDate", slot.getSlotDate().toString());
        map.put("startTime", slot.getStartTime().toString());
        map.put("endTime", slot.getEndTime().toString());
        map.put("status", slot.getStatus().name());
        map.put("blockReason", slot.getBlockReason());
        return map;
    }

    Map<String, Object> serviceSummary(ExtraService service) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("extraServiceId", service.getExtraServiceId());
        map.put("serviceName", service.getServiceName());
        map.put("serviceType", service.getServiceType().name());
        map.put("description", service.getDescription());
        map.put("unitName", service.getUnitName());
        map.put("unitPrice", service.getUnitPrice());
        map.put("stockQuantity", service.getStockQuantity());
        map.put("maxQuantityPerBooking", service.getMaxQuantityPerBooking());
        map.put("status", service.getStatus().name());
        return map;
    }

    Map<String, Object> issueSummary(Issue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getIssueId());
        map.put("title", issue.getTitle());
        map.put("description", issue.getDescription());
        map.put("status", issue.getStatus().name());
        map.put("reporter", issue.getReporter().getFullName());
        map.put("bookingCode", issue.getBooking() == null ? null : issue.getBooking().getBookingCode());
        map.put("fieldName", issue.getField() == null ? null : issue.getField().getFieldName());
        map.put("extraServiceName", issue.getExtraService() == null ? null : issue.getExtraService().getServiceName());
        map.put("assignedStaff", issue.getAssignedStaff() == null ? null : issue.getAssignedStaff().getFullName());
        map.put("resolutionNote", issue.getResolutionNote());
        map.put("resolvedAt", issue.getResolvedAt());
        return map;
    }

    Map<String, Object> bookingSummary(Booking booking) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bookingId", booking.getBookingId());
        map.put("bookingCode", booking.getBookingCode());
        map.put("customer", booking.getCustomer().getFullName());
        map.put("customerId", booking.getCustomer().getUserId());
        map.put("staff", booking.getStaff() == null ? null : booking.getStaff().getFullName());
        map.put("fieldName", booking.getSlot().getField().getFieldName());
        map.put("slotId", booking.getSlot().getSlotId());
        map.put("slotDate", booking.getSlot().getSlotDate().toString());
        map.put("startTime", booking.getSlot().getStartTime().toString());
        map.put("endTime", booking.getSlot().getEndTime().toString());
        map.put("status", booking.getStatus().name());
        map.put("bookingSource", booking.getBookingSource().name());
        map.put("fieldPriceAmount", booking.getFieldPriceAmount());
        map.put("serviceTotalAmount", booking.getServiceTotalAmount());
        map.put("promotionDiscountAmount", booking.getPromotionDiscountAmount());
        map.put("membershipDiscountAmount", booking.getMembershipDiscountAmount());
        map.put("totalAmount", booking.getTotalAmount());
        map.put("depositAmount", booking.getDepositAmount());
        map.put("paidAmount", booking.getPaidAmount());
        map.put("remainingAmount", booking.getRemainingAmount());
        map.put("cancellationFeeAmount", booking.getCancellationFeeAmount());
        map.put("refundableAmount", booking.getRefundableAmount());
        map.put("note", booking.getNote());
        return map;
    }

    String paymentStatus(Booking booking) {
        BigDecimal grossPaid = paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(booking.getBookingId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.paid
                        || payment.getStatus() == PaymentStatus.partially_refunded
                        || payment.getStatus() == PaymentStatus.refunded)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedRefunds = refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(booking.getBookingId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.completed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (completedRefunds.compareTo(BigDecimal.ZERO) > 0) {
            return completedRefunds.compareTo(grossPaid) >= 0 ? "refunded" : "partially_refunded";
        }
        if (booking.getStatus() == BookingStatus.expired) {
            return "expired";
        }
        if (booking.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            boolean hasFailedPayment = paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(booking.getBookingId()).stream()
                    .anyMatch(payment -> payment.getStatus() == PaymentStatus.failed);
            return hasFailedPayment ? "failed" : "unpaid";
        }
        return booking.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0 ? "paid" : "partially_paid";
    }

    Map<String, Object> bookingServiceSummary(BookingServiceItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceId", item.getExtraService().getExtraServiceId());
        map.put("serviceName", item.getExtraService().getServiceName());
        map.put("quantity", item.getQuantity());
        map.put("unitPrice", item.getUnitPrice());
        map.put("lineTotal", item.getLineTotal());
        return map;
    }

    Map<String, Object> paymentSummary(Payment payment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paymentId", payment.getPaymentId());
        map.put("bookingCode", payment.getBooking().getBookingCode());
        map.put("paymentCode", payment.getPaymentCode());
        map.put("paymentOption", payment.getPaymentOption().name());
        map.put("paymentMethod", payment.getPaymentMethod().name());
        map.put("amount", payment.getAmount());
        map.put("status", payment.getStatus().name());
        map.put("transactionCode", payment.getTransactionCode());
        map.put("currency", payment.getCurrency());
        map.put("providerFeeAmount", payment.getProviderFeeAmount());
        map.put("providerNetAmount", payment.getProviderNetAmount());
        map.put("providerFeeTracked", payment.getProviderFeeAmount() != null);
        map.put("gatewayMessage", payment.getGatewayMessage());
        map.put("paidAt", payment.getPaidAt());
        return map;
    }

    Map<String, Object> invoiceSummary(Invoice invoice) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("invoiceId", invoice.getInvoiceId());
        map.put("invoiceCode", invoice.getInvoiceCode());
        map.put("fieldAmount", invoice.getFieldAmount());
        map.put("serviceAmount", invoice.getServiceAmount());
        map.put("discountAmount", invoice.getDiscountAmount());
        map.put("totalAmount", invoice.getTotalAmount());
        map.put("paidAmount", invoice.getPaidAmount());
        map.put("remainingAmount", invoice.getRemainingAmount());
        map.put("refundAmount", invoice.getRefundAmount());
        map.put("issuedAt", invoice.getIssuedAt());
        return map;
    }

    Map<String, Object> refundSummary(Refund refund) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("refundId", refund.getRefundId());
        map.put("bookingId", refund.getBooking().getBookingId());
        map.put("bookingCode", refund.getBooking().getBookingCode());
        map.put("customer", refund.getBooking().getCustomer().getFullName());
        map.put("refundCode", refund.getRefundCode());
        map.put("refundAmount", refund.getRefundAmount());
        map.put("refundReason", refund.getRefundReason());
        map.put("status", refund.getStatus().name());
        map.put("transactionCode", refund.getTransactionCode());
        map.put("paymentId", refund.getPayment() == null ? null : refund.getPayment().getPaymentId());
        map.put("paymentMethod", refund.getPayment() == null ? null : refund.getPayment().getPaymentMethod().name());
        map.put("providerStatus", refund.getProviderStatus());
        map.put("gatewayMessage", refund.getGatewayMessage());
        map.put("requestedAt", refund.getRequestedAt());
        map.put("processedAt", refund.getProcessedAt());
        map.put("requestedBy", refund.getRequestedBy() == null ? null : refund.getRequestedBy().getFullName());
        map.put("processedBy", refund.getProcessedBy() == null ? null : refund.getProcessedBy().getFullName());
        return map;
    }

    Map<String, Object> promotionSummary(Promotion promotion) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promotionId", promotion.getPromotionId());
        map.put("promotionCode", promotion.getPromotionCode());
        map.put("promotionName", promotion.getPromotionName());
        map.put("bannerUrl", promotion.getBannerUrl());
        map.put("description", promotion.getDescription());
        map.put("discountType", promotion.getDiscountType().name());
        map.put("discountValue", promotion.getDiscountValue());
        map.put("maxDiscountAmount", promotion.getMaxDiscountAmount());
        map.put("minBookingAmount", promotion.getMinBookingAmount());
        map.put("startDate", promotion.getStartDate().toString());
        map.put("endDate", promotion.getEndDate().toString());
        map.put("usageLimit", promotion.getUsageLimit());
        map.put("usedCount", promotion.getUsedCount());
        map.put("status", promotion.getStatus().name());
        map.put("applicableFieldTypeId", promotion.getApplicableFieldType() != null ? promotion.getApplicableFieldType().getFieldTypeId() : null);
        map.put("applicableExtraServiceId", promotion.getApplicableExtraService() != null ? promotion.getApplicableExtraService().getExtraServiceId() : null);
        map.put("applicableMembershipLevelId", promotion.getApplicableMembershipLevel() != null ? promotion.getApplicableMembershipLevel().getMembershipLevelId() : null);
        map.put("applicableDayType", promotion.getApplicableDayType());
        map.put("applicableStartTime", promotion.getApplicableStartTime() == null ? null : promotion.getApplicableStartTime().toString());
        map.put("applicableEndTime", promotion.getApplicableEndTime() == null ? null : promotion.getApplicableEndTime().toString());
        map.put("stackable", promotion.isStackable());
        return map;
    }

    Map<String, Object> bookingPromotionSummary(BookingPromotion bookingPromotion) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promotionCode", bookingPromotion.getPromotionCodeSnapshot());
        map.put("discountAmount", bookingPromotion.getDiscountAmount());
        map.put("appliedAt", bookingPromotion.getAppliedAt());
        return map;
    }

    Map<String, Object> membershipLevelSummary(MembershipLevel level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("membershipLevelId", level.getMembershipLevelId());
        map.put("levelName", level.getLevelName());
        map.put("requiredCompletedBookings", level.getRequiredCompletedBookings());
        map.put("qualificationPeriod", level.getQualificationPeriod());
        map.put("requiredConsecutivePeriods", level.getRequiredConsecutivePeriods());
        map.put("discountPercent", level.getDiscountPercent());
        map.put("benefitDescription", level.getBenefitDescription());
        map.put("displayOrder", level.getDisplayOrder());
        map.put("status", level.getStatus().name());
        return map;
    }

    Map<String, Object> settingSummary(SystemSetting setting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("settingKey", setting.getSettingKey());
        map.put("settingValue", setting.getSettingValue());
        map.put("settingGroup", setting.getSettingGroup());
        map.put("description", setting.getDescription());
        return map;
    }

    Map<String, Object> notificationSummary(Notification notification) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("notificationId", notification.getNotificationId());
        map.put("title", notification.getTitle());
        map.put("message", notification.getMessage());
        map.put("type", notification.getNotificationType().name());
        map.put("read", notification.isRead());
        map.put("sentAt", notification.getSentAt());
        return map;
    }

    AppUser getUser(Long userId) {
        if (userId == null) {
            throw badRequest("User id is required");
        }
        return userRepository.findById(userId).orElseThrow(() -> notFound("User not found"));
    }

    FootballField getField(Long fieldId) {
        if (fieldId == null) {
            throw badRequest("Field id is required");
        }
        return fieldRepository.findById(fieldId).orElseThrow(() -> notFound("Field not found"));
    }

    Slot getSlot(Long slotId) {
        if (slotId == null) {
            throw badRequest("Slot id is required");
        }
        return slotRepository.findById(slotId).orElseThrow(() -> notFound("Slot not found"));
    }

    Booking getBooking(Long bookingId) {
        if (bookingId == null) {
            throw badRequest("Booking id is required");
        }
        return bookingRepository.findById(bookingId).orElseThrow(() -> notFound("Booking not found"));
    }

    ExtraService getExtraService(Long serviceId) {
        if (serviceId == null) {
            throw badRequest("Service id is required");
        }
        return extraServiceRepository.findById(serviceId).orElseThrow(() -> notFound("Extra service not found"));
    }

    void requireCurrentStatus(Booking booking, BookingStatus status) {
        if (booking.getStatus() != status) {
            throw badRequest("Booking must be " + status + " before this action");
        }
    }

    void requireText(String value, String message) {
        if (isBlank(value)) {
            throw badRequest(message);
        }
    }

    ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    ApiException serverError(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    String clean(String value) {
        return value == null ? null : value.trim();
    }

    String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }

    <T> T nvl(T value, T fallback) {
        return value == null ? fallback : value;
    }

    BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
