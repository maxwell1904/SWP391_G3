package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.DiscountType;
import com.swp391.backend.enums.PaymentStatus;
import com.swp391.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class PromotionReportService {
    private final DomainSupportService support;
    private final BookingWorkflowService bookingWorkflowService;
    private final SlotGenerationService slotGenerationService;

    public PromotionReportService(
            DomainSupportService support,
            BookingWorkflowService bookingWorkflowService,
            SlotGenerationService slotGenerationService
    ) {
        this.support = support;
        this.bookingWorkflowService = bookingWorkflowService;
        this.slotGenerationService = slotGenerationService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> promotions(boolean includeInactive) {
        if (includeInactive) {
            requireAdmin();
        }
        List<Promotion> promotions = includeInactive
                ? support.promotionRepository.findAll()
                : support.promotionRepository.findByStatus(CommonStatus.active);
        LocalDate today = LocalDate.now();
        return promotions.stream()
                .filter(promotion -> includeInactive
                        || (!today.isBefore(promotion.getStartDate())
                        && !today.isAfter(promotion.getEndDate())
                        && (promotion.getUsageLimit() == null || promotion.getUsedCount() < promotion.getUsageLimit())))
                .map(support::promotionSummary)
                .toList();
    }

    // Backlog owner: AnPTT - UC-46 Manage promotion campaigns.
    public Map<String, Object> createPromotion(ApiRequests.PromotionUpsert request) {
        Promotion promotion = new Promotion();
        applyPromotionFields(promotion, request, null);
        promotion.setUsedCount(0);
        return support.promotionSummary(support.promotionRepository.save(promotion));
    }

    // Backlog owner: AnPTT - UC-46 Manage promotion campaigns.
    public Map<String, Object> updatePromotion(Long promotionId, ApiRequests.PromotionUpsert request) {
        Promotion promotion = support.promotionRepository.findById(promotionId)
                .orElseThrow(() -> support.notFound("Promotion not found"));
        applyPromotionFields(promotion, request, promotionId);
        return support.promotionSummary(promotion);
    }

    public Map<String, Object> applyPromotionPreview(ApiRequests.PromotionApply request) {
        return bookingWorkflowService.previewCheckout(request);
    }

    public void reconcileMembershipAssignments() {
        support.refreshAllMembershipAssignments();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> membershipProgress(Long customerId) {
        requireSelfOrAdmin(customerId);
        AppUser customer = support.getUser(customerId);
        CustomerMembership membership = support.customerMembershipRepository.findByCustomer_UserId(customerId)
                .orElseThrow(() -> support.notFound("Membership not found"));
        List<MembershipLevel> levels = support.membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> level.getStatus() == CommonStatus.active)
                .toList();
        MembershipLevel current = support.resolveEligibleMembershipLevel(customer, LocalDate.now());
        MembershipLevel next = levels.stream()
                .filter(level -> level.getDisplayOrder() > current.getDisplayOrder())
                .findFirst()
                .orElse(null);
        int progress = next == null ? 0 : support.membershipQualificationProgress(customer, next, LocalDate.now());
        boolean weekly = next != null && "weekly".equalsIgnoreCase(next.getQualificationPeriod());
        int target = next == null ? 0 : (weekly ? next.getRequiredConsecutivePeriods() : next.getRequiredCompletedBookings());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", support.userSummary(customer));
        response.put("currentLevel", current.getLevelName());
        response.put("membershipLevel", support.membershipLevelSummary(current));
        response.put("completedBookingCount", support.completedBookingsInRange(customer, null, null));
        response.put("discountPercent", current.getDiscountPercent());
        response.put("nextLevel", next == null ? null : next.getLevelName());
        response.put("bookingsToNextLevel", next == null ? 0 : Math.max(0, target - progress));
        response.put("nextLevelProgress", progress);
        response.put("nextLevelTarget", target);
        response.put("nextLevelQualificationPeriod", next == null ? null : next.getQualificationPeriod());
        response.put("levels", levels.stream().map(support::membershipLevelSummary).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> membershipLevels(boolean includeInactive) {
        if (includeInactive) {
            requireAdmin();
        }
        return support.membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> includeInactive || level.getStatus() == CommonStatus.active)
                .map(support::membershipLevelSummary)
                .toList();
    }

    public Map<String, Object> createMembershipLevel(ApiRequests.MembershipLevelUpsert request) {
        MembershipLevel level = new MembershipLevel();
        applyMembershipLevelFields(level, request, null);
        MembershipLevel saved = support.membershipLevelRepository.save(level);
        support.refreshAllMembershipAssignments();
        return support.membershipLevelSummary(saved);
    }

    public Map<String, Object> updateMembershipLevel(Long id, ApiRequests.MembershipLevelUpsert request) {
        MembershipLevel level = support.membershipLevelRepository.findById(id)
                .orElseThrow(() -> support.badRequest("Membership level not found"));
        applyMembershipLevelFields(level, request, id);
        MembershipLevel saved = support.membershipLevelRepository.save(level);
        support.refreshAllMembershipAssignments();
        return support.membershipLevelSummary(saved);
    }

    private void applyMembershipLevelFields(MembershipLevel level, ApiRequests.MembershipLevelUpsert request, Long currentId) {
        String name = support.clean(request.levelName());
        support.requireText(name, "Level name is required");
        support.membershipLevelRepository.findAll().stream()
                .filter(existing -> existing.getLevelName().equalsIgnoreCase(name) && !existing.getMembershipLevelId().equals(currentId))
                .findFirst()
                .ifPresent(existing -> {
                    throw support.badRequest("Level name already exists");
                });

        level.setLevelName(name);
        int requiredBookings = request.requiredCompletedBookings() != null ? request.requiredCompletedBookings() : 0;
        BigDecimal discountPercent = request.discountPercent() != null ? request.discountPercent() : BigDecimal.ZERO;
        int displayOrder = request.displayOrder() != null ? request.displayOrder() : 0;
        if (requiredBookings < 0) throw support.badRequest("Required completed bookings cannot be negative");
        if (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw support.badRequest("Membership discount percent must be between 0 and 100");
        }
        if (displayOrder < 0) throw support.badRequest("Membership display order cannot be negative");
        level.setRequiredCompletedBookings(requiredBookings);
        level.setDiscountPercent(support.money(discountPercent));
        level.setBenefitDescription(support.clean(request.benefitDescription()));
        level.setDisplayOrder(displayOrder);
        level.setStatus(support.parseEnum(CommonStatus.class, request.status(), CommonStatus.active));
        String period = support.clean(request.qualificationPeriod());
        period = period == null ? "lifetime" : period.toLowerCase();
        if (!List.of("lifetime", "monthly", "weekly").contains(period)) {
            throw support.badRequest("Qualification period must be lifetime, monthly, or weekly");
        }
        int consecutive = request.requiredConsecutivePeriods() == null ? 1 : request.requiredConsecutivePeriods();
        if (consecutive < 1) throw support.badRequest("Consecutive periods must be at least one");
        level.setQualificationPeriod(period);
        level.setRequiredConsecutivePeriods(consecutive);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reports(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw support.badRequest("Report end date cannot be before start date");
        List<Booking> bookings = support.bookingRepository.findAll().stream()
                .filter(booking -> from == null || !booking.getSlot().getSlotDate().isBefore(from))
                .filter(booking -> to == null || !booking.getSlot().getSlotDate().isAfter(to))
                .toList();
        // Booking/occupancy metrics use the playing date. Revenue uses the
        // actual collection/refund date so a July payment for an August match
        // is reported in July, not August.
        List<Payment> payments = support.paymentRepository.findAll().stream()
                .filter(payment -> isWithinDateRange(payment.getPaidAt(), from, to))
                .toList();
        List<Refund> refunds = support.refundRepository.findAll().stream()
                .filter(refund -> isWithinDateRange(refund.getProcessedAt(), from, to))
                .toList();
        BigDecimal grossRevenue = payments.stream()
                .filter(this::isCollectedPayment)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal providerFees = payments.stream()
                .filter(this::isCollectedPayment)
                .map(Payment::getProviderFeeAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long untrackedProviderFeeCount = payments.stream()
                .filter(this::isCollectedPayment)
                .filter(payment -> payment.getPaymentMethod() == com.swp391.backend.enums.PaymentMethod.paypal_sandbox)
                .filter(payment -> payment.getProviderFeeAmount() == null)
                .count();
        BigDecimal completedRefunds = refunds.stream()
                .filter(refund -> refund.getStatus() == com.swp391.backend.enums.RefundStatus.completed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completed = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.completed).count();
        long cancelled = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.cancelled).count();
        long noShow = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.no_show).count();

        Map<String, Long> byField = new LinkedHashMap<>();
        Map<String, BigDecimal> bookedRevenueByField = new LinkedHashMap<>();
        Map<String, Long> peakSlots = new LinkedHashMap<>();
        Map<String, Long> bookingStatusCounts = new LinkedHashMap<>();
        Set<BookingStatus> occupancyStatuses = Set.of(
                BookingStatus.confirmed,
                BookingStatus.checked_in,
                BookingStatus.completed,
                BookingStatus.no_show
        );
        Set<BookingStatus> excludedCommercialStatuses = Set.of(
                BookingStatus.rejected,
                BookingStatus.expired,
                BookingStatus.cancelled
        );
        BigDecimal fieldValue = BigDecimal.ZERO;
        BigDecimal serviceValue = BigDecimal.ZERO;
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        BigDecimal membershipDiscount = BigDecimal.ZERO;
        for (Booking booking : bookings) {
            bookingStatusCounts.merge(booking.getStatus().name(), 1L, Long::sum);
            if (occupancyStatuses.contains(booking.getStatus())) {
                byField.merge(booking.getSlot().getField().getFieldName(), 1L, Long::sum);
                peakSlots.merge(booking.getSlot().getStartTime().toString(), 1L, Long::sum);
            }
            if (!excludedCommercialStatuses.contains(booking.getStatus())) {
                fieldValue = fieldValue.add(booking.getFieldPriceAmount());
                serviceValue = serviceValue.add(booking.getServiceTotalAmount());
                promotionDiscount = promotionDiscount.add(booking.getPromotionDiscountAmount());
                membershipDiscount = membershipDiscount.add(booking.getMembershipDiscountAmount());
                bookedRevenueByField.merge(booking.getSlot().getField().getFieldName(), booking.getFieldPriceAmount(), BigDecimal::add);
            }
        }

        Map<Long, Long> bookingCountByCustomer = bookings.stream()
                .filter(booking -> !excludedCommercialStatuses.contains(booking.getStatus()))
                .filter(booking -> booking.getCustomer() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        booking -> booking.getCustomer().getUserId(),
                        java.util.stream.Collectors.counting()
                ));
        List<Map<String, Object>> topCustomers = support.userRepository.findAll().stream()
                .filter(user -> "Customer".equals(user.getRole().getRoleName()))
                .map(user -> Map.<String, Object>of(
                        "customerId", user.getUserId(),
                        "fullName", user.getFullName(),
                        "bookingCount", bookingCountByCustomer.getOrDefault(user.getUserId(), 0L)
                ))
                .filter(item -> ((Number) item.get("bookingCount")).longValue() > 0)
                .sorted(Comparator.<Map<String, Object>>comparingLong(item -> ((Number) item.get("bookingCount")).longValue()).reversed())
                .toList();
        long returningCustomers = topCustomers.stream()
                .filter(customer -> ((Number) customer.get("bookingCount")).longValue() > 1)
                .count();
        Map<String, Long> membershipDistribution = new LinkedHashMap<>();
        support.customerMembershipRepository.findAll().forEach(membership ->
                membershipDistribution.merge(membership.getMembershipLevel().getLevelName(), 1L, Long::sum));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalRevenue", support.money(grossRevenue.subtract(completedRefunds).subtract(providerFees)));
        response.put("grossRevenue", support.money(grossRevenue));
        response.put("refundTotal", support.money(completedRefunds));
        response.put("providerFeeTotal", support.money(providerFees));
        response.put("providerFeeUntrackedCount", untrackedProviderFeeCount);
        response.put("fieldRevenue", support.money(fieldValue));
        response.put("serviceRevenue", support.money(serviceValue));
        response.put("promotionDiscountTotal", support.money(promotionDiscount));
        response.put("membershipDiscountTotal", support.money(membershipDiscount));
        response.put("bookingCount", bookings.size());
        response.put("completedCount", completed);
        response.put("cancelledCount", cancelled);
        response.put("noShowCount", noShow);
        response.put("fieldUtilization", byField);
        response.put("bookedRevenueByField", bookedRevenueByField);
        response.put("bookingStatusCounts", bookingStatusCounts);
        response.put("peakSlots", peakSlots);
        response.put("topCustomers", topCustomers);
        response.put("returningCustomerCount", returningCustomers);
        response.put("membershipDistribution", membershipDistribution);
        response.put("from", from == null ? null : from.toString());
        response.put("to", to == null ? null : to.toString());
        response.put("bookingDateBasis", "slotDate");
        response.put("revenueDateBasis", "paymentPaidAt/refundProcessedAt");
        return response;
    }

    private boolean isWithinDateRange(LocalDateTime timestamp, LocalDate from, LocalDate to) {
        if (timestamp == null) return from == null && to == null;
        LocalDate date = timestamp.toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private boolean isCollectedPayment(Payment payment) {
        return payment.getStatus() == PaymentStatus.paid
                || payment.getStatus() == PaymentStatus.partially_refunded
                || payment.getStatus() == PaymentStatus.refunded;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> settings(String group) {
        List<SystemSetting> settings = support.isBlank(group)
                ? support.systemSettingRepository.findAll()
                : support.systemSettingRepository.findBySettingGroupOrderBySettingKeyAsc(group);
        return settings.stream()
                .sorted(Comparator.comparing(SystemSetting::getSettingGroup).thenComparing(SystemSetting::getSettingKey))
                .map(support::settingSummary)
                .toList();
    }

    public Map<String, Object> updateSetting(String key, ApiRequests.SettingUpdate request) {
        SystemSetting setting = support.systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> support.notFound("Setting not found"));
        String value = support.clean(request.settingValue());
        support.requireText(value, "Setting value is required");
        validatePolicySetting(key, value);
        if (key.startsWith("slot.")) {
            slotGenerationService.validateRuleChange(key, value);
        }
        setting.setSettingValue(value);
        // The security context, not a request-body id, owns audit attribution.
        setting.setUpdatedBy(currentUser());
        Map<String, Object> response = new LinkedHashMap<>(support.settingSummary(setting));
        if (key.startsWith("slot.")) {
            SlotGenerationService.GenerationResult generated = slotGenerationService.rebuildRollingWindow();
            response.put("generatedSlotCount", generated.createdSlots());
            response.put("generatedThrough", generated.toDate().toString());
        }
        return response;
    }

    private void validatePolicySetting(String key, String value) {
        try {
            if (key.equals("deposit.default_percent") || key.startsWith("refund.")) {
                BigDecimal percent = new BigDecimal(value);
                if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw support.badRequest("Percentage policy values must be between 0 and 100");
                }
            }
            if (key.equals("payment.pending_timeout_minutes") || key.equals("notification.booking_reminder_hours")) {
                if (Integer.parseInt(value) < 1) throw support.badRequest("Time-based policy values must be at least 1");
            }
        } catch (NumberFormatException exception) {
            throw support.badRequest("Policy value must be numeric");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> notifications(Long userId) {
        requireSelfOrAdmin(userId);
        return support.notificationRepository.findByUser_UserIdOrderByNotificationIdDesc(userId).stream()
                .map(support::notificationSummary)
                .toList();
    }

    // Supporting notification inbox behavior for UC-51/52/53.
    public Map<String, Object> toggleNotificationRead(Long notificationId) {
        com.swp391.backend.entity.Notification notification = support.notificationRepository.findById(notificationId)
                .orElseThrow(() -> support.notFound("Notification not found"));
        requireSelfOrAdmin(notification.getUser().getUserId());
        notification.setRead(!notification.isRead());
        return support.notificationSummary(support.notificationRepository.save(notification));
    }

    // Supporting notification inbox behavior for UC-51/52/53.
    public Map<String, Object> markAllNotificationsRead(Long userId) {
        requireSelfOrAdmin(userId);
        List<com.swp391.backend.entity.Notification> unread =
                support.notificationRepository.findByUser_UserIdOrderByNotificationIdDesc(userId)
                        .stream()
                        .filter(n -> !n.isRead())
                        .toList();
        unread.forEach(n -> n.setRead(true));
        support.notificationRepository.saveAll(unread);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("markedCount", unread.size());
        return result;
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        return user.getAppUser();
    }

    private void requireSelfOrAdmin(Long userId) {
        AppUser requester = currentUser();
        if (!requester.getUserId().equals(userId) && !"Admin".equalsIgnoreCase(requester.getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only access your own account data");
        }
    }

    private void requireAdmin() {
        if (!"Admin".equalsIgnoreCase(currentUser().getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
    }

    private void applyPromotionFields(Promotion promotion, ApiRequests.PromotionUpsert request, Long currentId) {
        String code = support.clean(request.promotionCode());
        String name = support.clean(request.promotionName());
        support.requireText(code, "Promotion code is required");
        support.requireText(name, "Promotion name is required");
        if (request.discountValue() == null || request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("Discount value must be greater than zero");
        }
        if (request.startDate() == null || request.endDate() == null || request.endDate().isBefore(request.startDate())) {
            throw support.badRequest("Promotion date range is invalid");
        }
        support.promotionRepository.findByPromotionCodeIgnoreCase(code)
                .filter(existing -> !existing.getPromotionId().equals(currentId))
                .ifPresent(existing -> {
                    throw support.badRequest("Promotion code already exists");
                });

        DiscountType discountType = support.parseEnum(DiscountType.class, request.discountType(), DiscountType.percent);
        if (discountType == DiscountType.percent && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw support.badRequest("Percentage discount cannot exceed 100");
        }
        if (request.maxDiscountAmount() != null && request.maxDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw support.badRequest("Maximum discount amount cannot be negative");
        }
        if (request.minBookingAmount() != null && request.minBookingAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw support.badRequest("Minimum booking amount cannot be negative");
        }
        if (request.usageLimit() != null && request.usageLimit() < 0) {
            throw support.badRequest("Promotion usage limit cannot be negative");
        }

        promotion.setPromotionCode(code.toUpperCase());
        promotion.setPromotionName(name);
        promotion.setDescription(support.clean(request.description()));
        promotion.setBannerUrl(support.clean(request.bannerUrl()));
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(support.money(request.discountValue()));
        promotion.setMaxDiscountAmount(request.maxDiscountAmount() == null ? null : support.money(request.maxDiscountAmount()));
        promotion.setMinBookingAmount(request.minBookingAmount() == null ? null : support.money(request.minBookingAmount()));
        promotion.setUsageLimit(request.usageLimit());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setStatus(support.parseEnum(CommonStatus.class, request.status(), CommonStatus.active));
        
        if (request.applicableFieldTypeId() != null) {
            promotion.setApplicableFieldType(support.fieldTypeRepository.findById(request.applicableFieldTypeId())
                    .orElseThrow(() -> support.badRequest("Invalid Field Type ID")));
        } else {
            promotion.setApplicableFieldType(null);
        }
        
        if (request.applicableExtraServiceId() != null) {
            promotion.setApplicableExtraService(support.extraServiceRepository.findById(request.applicableExtraServiceId())
                    .orElseThrow(() -> support.badRequest("Invalid Extra Service ID")));
        } else {
            promotion.setApplicableExtraService(null);
        }
        
        if (request.applicableMembershipLevelId() != null) {
            promotion.setApplicableMembershipLevel(support.membershipLevelRepository.findById(request.applicableMembershipLevelId())
                    .orElseThrow(() -> support.badRequest("Invalid Membership Level ID")));
        } else {
            promotion.setApplicableMembershipLevel(null);
        }

        String dayType = support.clean(request.applicableDayType());
        if (!support.isBlank(dayType) && !List.of("all", "weekday", "weekend").contains(dayType.toLowerCase())) {
            throw support.badRequest("Applicable day type must be all, weekday, or weekend");
        }
        promotion.setApplicableDayType(support.isBlank(dayType) ? null : dayType.toLowerCase());
        boolean hasStartTime = !support.isBlank(request.applicableStartTime());
        boolean hasEndTime = !support.isBlank(request.applicableEndTime());
        if (hasStartTime != hasEndTime) {
            throw support.badRequest("Promotion start and end time must be provided together");
        }
        if (hasStartTime) {
            try {
                LocalTime startTime = LocalTime.parse(request.applicableStartTime());
                LocalTime endTime = LocalTime.parse(request.applicableEndTime());
                if (!endTime.isAfter(startTime)) throw support.badRequest("Promotion end time must be after start time");
                promotion.setApplicableStartTime(startTime);
                promotion.setApplicableEndTime(endTime);
            } catch (java.time.format.DateTimeParseException exception) {
                throw support.badRequest("Promotion time range is invalid");
            }
        } else {
            promotion.setApplicableStartTime(null);
            promotion.setApplicableEndTime(null);
        }
        promotion.setStackable(Boolean.TRUE.equals(request.stackable()));
    }
}
