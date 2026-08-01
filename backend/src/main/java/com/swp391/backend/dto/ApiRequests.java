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
            String address
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
            String guestName,
            String guestPhone,
            String guestEmail,
            Long slotId,
            String bookingSource,
            String promotionCode,
            List<ServiceSelection> services,
            String note
    ) {
        public BookingCreate(Long customerId, Long slotId, String bookingSource, String promotionCode,
                             List<ServiceSelection> services, String note) {
            this(customerId, null, null, null, slotId, bookingSource, promotionCode, services, note);
        }
    }

    public record BookingStatusUpdate(
            String status,
            String note
    ) {
    }

    /** Moves an unstarted booking to another available slot. */
    public record BookingReschedule(
            Long newSlotId,
            String note
    ) {
    }

    public record IssueCreate(
            Long bookingId,
            Long fieldId,
            Long extraServiceId,
            String title,
            String description
    ) {
    }

    public record IssueStatusUpdate(
            String status,
            String resolutionNote
    ) {
    }

    public record PaymentCapture(
            Long bookingId,
            String paymentOption,
            String paymentMethod,
            BigDecimal amount,
            boolean success
    ) {
    }

    /**
     * Creates a Staff-owned walk-in booking and records its initial cash payment
     * inside one backend transaction. Pay-later remains the ordinary
     * BookingCreate flow and deliberately skips this request.
     */
    public record WalkInCheckout(
            Long customerId,
            String guestName,
            String guestPhone,
            String guestEmail,
            Long slotId,
            String promotionCode,
            List<ServiceSelection> services,
            String note,
            String paymentOption
    ) {
    }

    public record PayPalOrderCreate(
            String paymentOption
    ) {
    }

    public record RefundCreate(
            Long bookingId,
            Long paymentId,
            BigDecimal refundAmount,
            String refundReason
    ) {
    }

    /** Staff-only lifecycle action for a customer refund request. */
    public record RefundStatusUpdate(
            String status,
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
            String settingValue
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
            String blockNote
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

}
