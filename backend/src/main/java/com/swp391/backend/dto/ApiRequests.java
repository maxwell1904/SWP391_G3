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

    public record RestrictionUpdate(
            boolean bookingRestricted,
            String restrictionReason
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
            String status
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
}
