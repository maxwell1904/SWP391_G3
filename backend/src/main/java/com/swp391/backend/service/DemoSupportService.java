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
import java.util.*;

@Service
public class DemoSupportService {
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

    public DemoSupportService(
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
        if (slot.getStatus() == SlotStatus.blocked) {
            throw badRequest("Slot is blocked: " + nvl(slot.getBlockReason(), "unavailable"));
        }
        if (slot.getSlotDate().isBefore(LocalDate.now())) {
            throw badRequest("Past slots cannot be booked");
        }
        if (bookingRepository.existsBySlotAndStatusIn(slot, ACTIVE_BOOKING_STATUSES)) {
            throw badRequest("Slot already has an active booking");
        }
    }

    BigDecimal calculateFieldPrice(Slot slot) {
        String dayType = slot.getSlotDate().getDayOfWeek() == DayOfWeek.SATURDAY || slot.getSlotDate().getDayOfWeek() == DayOfWeek.SUNDAY
                ? "weekend"
                : "weekday";
        return fieldPriceRepository.findByField_FieldId(slot.getField().getFieldId()).stream()
                .filter(price -> price.getStatus() == CommonStatus.active)
                .filter(price -> price.getDayType().equalsIgnoreCase(dayType) || price.getDayType().equalsIgnoreCase("all"))
                .filter(price -> !slot.getStartTime().isBefore(price.getStartTime()) && !slot.getEndTime().isAfter(price.getEndTime()))
                .findFirst()
                .map(FieldPrice::getPrice)
                .orElse(BigDecimal.valueOf(300000));
    }

    BigDecimal calculateServiceTotal(List<ApiRequests.ServiceSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ApiRequests.ServiceSelection selection : selections) {
            ExtraService service = getExtraService(selection.serviceId());
            int quantity = validateServiceQuantity(service, selection.quantity());
            total = total.add(service.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        return money(total);
    }

    BigDecimal calculatePromotionDiscount(String promotionCode, Slot slot, BigDecimal baseAmount, List<ApiRequests.ServiceSelection> services) {
        if (isBlank(promotionCode)) {
            return BigDecimal.ZERO;
        }
        Promotion promotion = promotionRepository.findByPromotionCodeIgnoreCase(promotionCode)
                .orElseThrow(() -> badRequest("Promotion not found"));
        LocalDate today = LocalDate.now();
        if (promotion.getStatus() != CommonStatus.active || today.isBefore(promotion.getStartDate()) || today.isAfter(promotion.getEndDate())) {
            throw badRequest("Promotion is not active");
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw badRequest("Promotion usage limit reached");
        }
        if (promotion.getMinBookingAmount() != null && baseAmount.compareTo(promotion.getMinBookingAmount()) < 0) {
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
        BigDecimal discount = promotion.getDiscountType() == DiscountType.percent
                ? baseAmount.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        if (promotion.getMaxDiscountAmount() != null) {
            discount = discount.min(promotion.getMaxDiscountAmount());
        }
        return money(discount);
    }

    BigDecimal calculateMembershipDiscount(AppUser customer, BigDecimal baseAmount, BookingSource source) {
        if (customer == null || source != BookingSource.online) {
            return BigDecimal.ZERO;
        }
        return customerMembershipRepository.findByCustomer_UserId(customer.getUserId())
                .map(membership -> money(baseAmount.multiply(membership.getMembershipLevel().getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)))
                .orElse(BigDecimal.ZERO);
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
        BigDecimal refundable = money(paid.multiply(refundPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal fee = money(paid.subtract(refundable).max(BigDecimal.ZERO));
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("bookingId", booking.getBookingId());
        preview.put("bookingCode", booking.getBookingCode());
        preview.put("hoursBeforeStart", hoursBeforeStart);
        preview.put("paidAmount", paid);
        preview.put("refundPercent", refundPercent);
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

    private BigDecimal settingDecimal(String key, BigDecimal fallback) {
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
        membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(level -> completedCount >= level.getRequiredCompletedBookings())
                .reduce((first, second) -> second)
                .ifPresent(membership::setMembershipLevel);
        membership.setProgressNote("Updated after completed booking");
    }

    CustomerMembership attachDefaultMembership(AppUser customer) {
        MembershipLevel defaultLevel = membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .findFirst()
                .orElseThrow(() -> serverError("Membership levels have not been seeded"));
        CustomerMembership membership = new CustomerMembership();
        membership.setCustomer(customer);
        membership.setMembershipLevel(defaultLevel);
        membership.setEffectiveFrom(LocalDate.now());
        membership.setProgressNote("Default membership");
        return customerMembershipRepository.save(membership);
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
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("role", user.getRole().getRoleName());
        map.put("status", user.getStatus().name());
        map.put("bookingRestricted", user.isBookingRestricted());
        map.put("emailVerified", user.isEmailVerified());
        map.put("restrictionReason", user.getRestrictionReason());
        map.put("lastLoginAt", user.getLastLoginAt());
        return map;
    }

    Map<String, Object> fieldSummary(FootballField field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldId", field.getFieldId());
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
        map.put("unitName", service.getUnitName());
        map.put("unitPrice", service.getUnitPrice());
        map.put("stockQuantity", service.getStockQuantity());
        map.put("maxQuantityPerBooking", service.getMaxQuantityPerBooking());
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
        BigDecimal completedRefunds = refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(booking.getBookingId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.completed)
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (completedRefunds.compareTo(BigDecimal.ZERO) > 0) {
            return completedRefunds.compareTo(booking.getPaidAmount()) >= 0 ? "refunded" : "partially_refunded";
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
        map.put("bookingCode", refund.getBooking().getBookingCode());
        map.put("refundCode", refund.getRefundCode());
        map.put("refundAmount", refund.getRefundAmount());
        map.put("refundReason", refund.getRefundReason());
        map.put("status", refund.getStatus().name());
        map.put("transactionCode", refund.getTransactionCode());
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
        map.put("discountPercent", level.getDiscountPercent());
        map.put("benefitDescription", level.getBenefitDescription());
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
