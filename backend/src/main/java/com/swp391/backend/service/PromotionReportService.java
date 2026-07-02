package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.DiscountType;
import com.swp391.backend.enums.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        return promotions.stream().map(support::promotionSummary).toList();
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
        AppUser customer = support.getUser(customerId);
        CustomerMembership membership = support.customerMembershipRepository.findByCustomer_UserId(customerId)
                .orElseThrow(() -> support.notFound("Membership not found"));
        List<MembershipLevel> levels = support.membershipLevelRepository.findAllByOrderByDisplayOrderAsc();
        MembershipLevel next = levels.stream()
                .filter(level -> level.getRequiredCompletedBookings() > membership.getCompletedBookingCount())
                .findFirst()
                .orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", support.userSummary(customer));
        response.put("currentLevel", membership.getMembershipLevel().getLevelName());
        response.put("completedBookingCount", membership.getCompletedBookingCount());
        response.put("discountPercent", membership.getMembershipLevel().getDiscountPercent());
        response.put("nextLevel", next == null ? null : next.getLevelName());
        response.put("bookingsToNextLevel", next == null ? 0 : next.getRequiredCompletedBookings() - membership.getCompletedBookingCount());
        response.put("levels", levels.stream().map(support::membershipLevelSummary).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> membershipLevels() {
        return support.membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
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
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reports() {
        List<Booking> bookings = support.bookingRepository.findAll();
        List<Payment> payments = support.paymentRepository.findAll();
        BigDecimal revenue = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.paid)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completed = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.completed).count();
        long cancelled = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.cancelled).count();
        long noShow = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.no_show).count();

        Map<String, Long> byField = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            byField.merge(booking.getSlot().getField().getFieldName(), 1L, Long::sum);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalRevenue", support.money(revenue));
        response.put("bookingCount", bookings.size());
        response.put("completedCount", completed);
        response.put("cancelledCount", cancelled);
        response.put("noShowCount", noShow);
        response.put("fieldUtilization", byField);
        response.put("topCustomers", support.userRepository.findAll().stream()
                .filter(user -> "Customer".equals(user.getRole().getRoleName()))
                .map(user -> Map.<String, Object>of(
                        "customerId", user.getUserId(),
                        "fullName", user.getFullName(),
                        "bookingCount", support.bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(user.getUserId()).size()
                ))
                .toList());
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
        setting.setSettingValue(request.settingValue());
        if (request.updatedById() != null) {
            setting.setUpdatedBy(support.getUser(request.updatedById()));
        }
        return support.settingSummary(setting);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> notifications(Long userId) {
        return support.notificationRepository.findByUser_UserIdOrderByNotificationIdDesc(userId).stream()
                .map(support::notificationSummary)
                .toList();
    }

    // Backlog owner: AnPTT - UC-57/58/59 Notification read/unread toggle.
    public Map<String, Object> toggleNotificationRead(Long notificationId) {
        com.swp391.backend.entity.Notification notification = support.notificationRepository.findById(notificationId)
                .orElseThrow(() -> support.notFound("Notification not found"));
        notification.setRead(!notification.isRead());
        return support.notificationSummary(support.notificationRepository.save(notification));
    }

    // Backlog owner: AnPTT - UC-57/58/59 Mark all notifications as read.
    public Map<String, Object> markAllNotificationsRead(Long userId) {
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
    }
}
