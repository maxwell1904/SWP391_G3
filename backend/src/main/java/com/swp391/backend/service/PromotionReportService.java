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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PromotionReportService {
    private final DemoSupportService support;
    private final BookingWorkflowService bookingWorkflowService;

    public PromotionReportService(DemoSupportService support, BookingWorkflowService bookingWorkflowService) {
        this.support = support;
        this.bookingWorkflowService = bookingWorkflowService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> promotions(boolean includeInactive) {
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

    // Backlog owner: AnPTT - UC-52 Manage promotion campaigns.
    public Map<String, Object> createPromotion(ApiRequests.PromotionUpsert request) {
        Promotion promotion = new Promotion();
        applyPromotionFields(promotion, request, null);
        promotion.setUsedCount(0);
        return support.promotionSummary(support.promotionRepository.save(promotion));
    }

    // Backlog owner: AnPTT - UC-52 Manage promotion campaigns.
    public Map<String, Object> updatePromotion(Long promotionId, ApiRequests.PromotionUpsert request) {
        Promotion promotion = support.promotionRepository.findById(promotionId)
                .orElseThrow(() -> support.notFound("Promotion not found"));
        applyPromotionFields(promotion, request, promotionId);
        return support.promotionSummary(promotion);
    }

    public Map<String, Object> applyPromotionPreview(ApiRequests.PromotionApply request) {
        return bookingWorkflowService.previewCheckout(request);
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
        response.put("completedBookingCount", membership.getCompletedBookingCount());
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
        return support.membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> includeInactive || level.getStatus() == CommonStatus.active)
                .map(support::membershipLevelSummary)
                .toList();
    }

    public Map<String, Object> createMembershipLevel(ApiRequests.MembershipLevelUpsert request) {
        MembershipLevel level = new MembershipLevel();
        applyMembershipLevelFields(level, request, null);
        return support.membershipLevelSummary(support.membershipLevelRepository.save(level));
    }

    public Map<String, Object> updateMembershipLevel(Long id, ApiRequests.MembershipLevelUpsert request) {
        MembershipLevel level = support.membershipLevelRepository.findById(id)
                .orElseThrow(() -> support.badRequest("Membership level not found"));
        applyMembershipLevelFields(level, request, id);
        return support.membershipLevelSummary(support.membershipLevelRepository.save(level));
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
        level.setRequiredCompletedBookings(request.requiredCompletedBookings() != null ? request.requiredCompletedBookings() : 0);
        level.setDiscountPercent(request.discountPercent() != null ? request.discountPercent() : BigDecimal.ZERO);
        level.setBenefitDescription(support.clean(request.benefitDescription()));
        level.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
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
        java.util.Set<Long> bookingIds = bookings.stream().map(Booking::getBookingId).collect(java.util.stream.Collectors.toSet());
        List<Payment> payments = support.paymentRepository.findAll().stream()
                .filter(payment -> bookingIds.contains(payment.getBooking().getBookingId()))
                .toList();
        List<Refund> refunds = support.refundRepository.findAll().stream()
                .filter(refund -> bookingIds.contains(refund.getBooking().getBookingId()))
                .toList();
        BigDecimal grossRevenue = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.paid)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        BigDecimal fieldValue = BigDecimal.ZERO;
        BigDecimal serviceValue = BigDecimal.ZERO;
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        BigDecimal membershipDiscount = BigDecimal.ZERO;
        for (Booking booking : bookings) {
            byField.merge(booking.getSlot().getField().getFieldName(), 1L, Long::sum);
            peakSlots.merge(booking.getSlot().getStartTime().toString(), 1L, Long::sum);
            bookingStatusCounts.merge(booking.getStatus().name(), 1L, Long::sum);
            if (booking.getStatus() != BookingStatus.rejected && booking.getStatus() != BookingStatus.expired) {
                fieldValue = fieldValue.add(booking.getFieldPriceAmount());
                serviceValue = serviceValue.add(booking.getServiceTotalAmount());
                promotionDiscount = promotionDiscount.add(booking.getPromotionDiscountAmount());
                membershipDiscount = membershipDiscount.add(booking.getMembershipDiscountAmount());
                bookedRevenueByField.merge(booking.getSlot().getField().getFieldName(), booking.getFieldPriceAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> topCustomers = support.userRepository.findAll().stream()
                .filter(user -> "Customer".equals(user.getRole().getRoleName()))
                .map(user -> Map.<String, Object>of(
                        "customerId", user.getUserId(),
                        "fullName", user.getFullName(),
                        "bookingCount", support.bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(user.getUserId()).size()
                ))
                .sorted(Comparator.<Map<String, Object>>comparingLong(item -> ((Number) item.get("bookingCount")).longValue()).reversed())
                .toList();
        long returningCustomers = topCustomers.stream()
                .filter(customer -> ((Number) customer.get("bookingCount")).longValue() > 1)
                .count();
        Map<String, Long> membershipDistribution = new LinkedHashMap<>();
        support.customerMembershipRepository.findAll().forEach(membership ->
                membershipDistribution.merge(membership.getMembershipLevel().getLevelName(), 1L, Long::sum));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalRevenue", support.money(grossRevenue.subtract(completedRefunds)));
        response.put("grossRevenue", support.money(grossRevenue));
        response.put("refundTotal", support.money(completedRefunds));
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
        response.put("dateBasis", "slotDate");
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> settings(String group) {
        List<SystemSetting> settings = support.isBlank(group)
                ? support.systemSettingRepository.findAll()
                : support.systemSettingRepository.findBySettingGroupOrderBySettingKeyAsc(group);
        return settings.stream().map(support::settingSummary).toList();
    }

    public Map<String, Object> updateSetting(String key, ApiRequests.SettingUpdate request) {
        SystemSetting setting = support.systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> support.notFound("Setting not found"));
        String value = support.clean(request.settingValue());
        support.requireText(value, "Setting value is required");
        validatePolicySetting(key, value);
        setting.setSettingValue(value);
        if (request.updatedById() != null) {
            setting.setUpdatedBy(support.getUser(request.updatedById()));
        }
        return support.settingSummary(setting);
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

    // Backlog owner: AnPTT - UC-57/58/59 Notification read/unread toggle.
    public Map<String, Object> toggleNotificationRead(Long notificationId) {
        com.swp391.backend.entity.Notification notification = support.notificationRepository.findById(notificationId)
                .orElseThrow(() -> support.notFound("Notification not found"));
        requireSelfOrAdmin(notification.getUser().getUserId());
        notification.setRead(!notification.isRead());
        return support.notificationSummary(support.notificationRepository.save(notification));
    }

    // Backlog owner: AnPTT - UC-57/58/59 Mark all notifications as read.
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
