package com.swp391.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiRequests {
    private ApiRequests() {
    }

    public record Register(
            String fullName,
            String email,
            String phone,
            String password,
            String confirmPassword
    ) {
    }

    public record Login(
            String emailOrPhone,
            String password
    ) {
    }

    public record EmailVerification(
            Long userId,
            String token
    ) {
    }

    public record EmailVerificationResend(
            Long userId
    ) {
    }

    public record ForgotPassword(
            String email
    ) {
    }

    public record ResetPassword(
            Long userId,
            String token,
            String newPassword,
            String confirmPassword
    ) {
    }

    public record ValidateResetToken(
            Long userId,
            String token
    ) {
    }

    public record ProfileUpdate(
            String fullName,
            String phone,
            String address,
            String avatarUrl
    ) {
    }

    public record PasswordChange(
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {
    }

    public record AccountLockUpdate(
            boolean accountLocked,
            String lockReason
    ) {
    }

    public record AccountStatusUpdate(String status) {
    }

    public record StaffUpsert(
            String fullName,
            String email,
            String phone,
            String password,
            String status
    ) {
    }

    public record ServiceSelection(
            Long serviceId,
            Integer quantity
    ) {
    }

    public record BookingCreate(
            Long customerId,
            Long staffId,
            Long slotId,
            String bookingSource,
            String promotionCode,
            List<ServiceSelection> services,
            String note
    ) {
    }

    public record BookingStatusUpdate(
            String status,
            Long staffId,
            String note
    ) {
    }

    /** Moves an unstarted booking to another available slot. */
    public record BookingReschedule(
            Long newSlotId,
            Long staffId,
            String note
    ) {
    }

    public record IssueCreate(
            Long reporterId,
            Long bookingId,
            Long fieldId,
            Long extraServiceId,
            Long assignedStaffId,
            String title,
            String description
    ) {
    }

    public record IssueStatusUpdate(
            String status,
            String resolutionNote,
            Long assignedStaffId
    ) {
    }

    public record PaymentCapture(
            Long bookingId,
            Long createdById,
            String paymentOption,
            String paymentMethod,
            BigDecimal amount,
            boolean success
    ) {
    }

    public record PayPalOrderCreate(
            Long createdById,
            String paymentOption
    ) {
    }

    public record PayPalOrderCapture(
            Long createdById
    ) {
    }

    public record RefundCreate(
            Long bookingId,
            Long paymentId,
            Long requestedById,
            Long processedById,
            BigDecimal refundAmount,
            String refundReason,
            boolean approveNow
    ) {
    }

    /** Staff-only lifecycle action for a customer refund request. */
    public record RefundStatusUpdate(
            String status,
            Long processedById,
            String note
    ) {
    }

    public record PromotionApply(
            Long customerId,
            Long slotId,
            String promotionCode,
            String bookingSource,
            List<ServiceSelection> services
    ) {
    }

    public record PromotionUpsert(
            String promotionCode,
            String promotionName,
            String description,
            String bannerUrl,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            BigDecimal minBookingAmount,
            Integer usageLimit,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Long applicableFieldTypeId,
            Long applicableExtraServiceId,
            Long applicableMembershipLevelId,
            String applicableDayType,
            String applicableStartTime,
            String applicableEndTime,
            Boolean stackable
    ) {
    }

    public record SettingUpdate(
            String settingValue,
            Long updatedById
    ) {
    }

    public record FieldUpsert(
            Long fieldTypeId,
            String fieldName,
            String description,
            String imageUrl,
            String location,
            String surfaceType,
            String status
    ) {
    }

    public record FieldPriceUpsert(
            String dayType,
            String startTime,
            String endTime,
            BigDecimal price,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status
    ) {
    }

    public record ExtraServiceUpsert(
            String serviceName,
            String serviceType,
            String description,
            String unitName,
            BigDecimal unitPrice,
            Integer stockQuantity,
            Integer maxQuantityPerBooking,
            String status
    ) {
    }

    public record SlotBlock(
            Long fieldId,
            LocalDate slotDate,
            String startTime,
            String endTime,
            String blockReason,
            String blockNote,
            Long createdById
    ) {
    }

    public record BookingServicesUpdate(List<ServiceSelection> services) {
    }

    public record MembershipLevelUpsert(
            String levelName,
            Integer requiredCompletedBookings,
            BigDecimal discountPercent,
            String benefitDescription,
            Integer displayOrder,
            String status,
            String qualificationPeriod,
            Integer requiredConsecutivePeriods
    ) {
    }

    /** Natural-language request for UC-63. Availability is still resolved by the backend. */
    public record AssistantAvailability(String question) {
    }
}
