package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import com.swp391.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class MvpDemoService {

    private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.pending,
            BookingStatus.confirmed,
            BookingStatus.checked_in
    );

    private final RoleRepository roleRepository;
    private final AppUserRepository userRepository;
    private final MembershipLevelRepository membershipLevelRepository;
    private final CustomerMembershipRepository customerMembershipRepository;
    private final FieldTypeRepository fieldTypeRepository;
    private final FootballFieldRepository fieldRepository;
    private final FieldPriceRepository fieldPriceRepository;
    private final SlotRepository slotRepository;
    private final ExtraServiceRepository extraServiceRepository;
    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final IssueRepository issueRepository;
    private final PromotionRepository promotionRepository;
    private final BookingPromotionRepository bookingPromotionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final RefundRepository refundRepository;
    private final NotificationRepository notificationRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final DataSource dataSource;

    public MvpDemoService(
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
            SystemSettingRepository systemSettingRepository,
            DataSource dataSource
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
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Backend API is running");
        payload.put("stack", "Spring Boot + JPA");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            payload.put("database", metaData.getDatabaseProductName());
            payload.put("jdbcUrl", metaData.getURL());
        } catch (SQLException exception) {
            payload.put("database", "unknown");
        }
        return payload;
    }

    public Map<String, Object> register(ApiRequests.Register request) {
        requireText(request.fullName(), "Full name is required");
        requireText(request.password(), "Password is required");
        if (isBlank(request.email()) && isBlank(request.phone())) {
            throw badRequest("Email or phone is required");
        }
        request.email();
        if (!isBlank(request.email()) && userRepository.findByEmail(request.email()).isPresent()) {
            throw badRequest("Email already exists");
        }
        if (!isBlank(request.phone()) && userRepository.findByPhone(request.phone()).isPresent()) {
            throw badRequest("Phone already exists");
        }

        Role customerRole = roleRepository.findByRoleName("Customer")
                .orElseThrow(() -> serverError("Customer role has not been seeded"));
        AppUser user = new AppUser();
        user.setRole(customerRole);
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(request.password());
        user.setEmailVerified(isBlank(request.email()));
        if (!user.isEmailVerified()) {
            issueEmailVerification(user);
        }
        user = userRepository.save(user);
        if (!user.isEmailVerified()) {
            notifyUser(user, null, NotificationType.system, "Verify email", "Use code " + user.getEmailVerificationToken() + " to verify your email.");
        }
        attachDefaultMembership(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", userSummary(user));
        response.put("verificationRequired", !user.isEmailVerified());
        response.put("verificationCode", user.getEmailVerificationToken());
        response.put("message", user.isEmailVerified()
                ? "Account created"
                : "Account created. Verify email before online booking.");
        return response;
    }

    public Map<String, Object> login(ApiRequests.Login request) {
        requireText(request.emailOrPhone(), "Email or phone is required");
        requireText(request.password(), "Password is required");
        AppUser user = userRepository.findByEmail(request.emailOrPhone())
                .or(() -> userRepository.findByPhone(request.emailOrPhone()))
                .orElseThrow(() -> notFound("Account not found"));
        if (!Objects.equals(user.getPasswordHash(), request.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getStatus() != AccountStatus.active) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        user.setLastLoginAt(LocalDateTime.now());
        return Map.of(
                "token", "demo-token-" + user.getUserId(),
                "user", userSummary(user),
                "note", "Demo login only. Replace with JWT before production."
        );
    }

    public Map<String, Object> verifyEmail(ApiRequests.EmailVerification request) {
        AppUser user = getUser(request.userId());
        if (user.isEmailVerified()) {
            return Map.of("user", userSummary(user), "message", "Email is already verified");
        }
        requireText(request.token(), "Verification code is required");
        if (!Objects.equals(user.getEmailVerificationToken(), request.token().trim())) {
            throw badRequest("Invalid verification code");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationSentAt(null);
        return Map.of("user", userSummary(user), "message", "Email verified");
    }

    public Map<String, Object> resendEmailVerification(ApiRequests.EmailVerificationResend request) {
        AppUser user = getUser(request.userId());
        if (isBlank(user.getEmail())) {
            throw badRequest("Account does not have an email address");
        }
        if (user.isEmailVerified()) {
            return Map.of("user", userSummary(user), "message", "Email is already verified");
        }
        String token = issueEmailVerification(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", userSummary(user));
        response.put("verificationCode", token);
        response.put("message", "Verification email queued for demo");
        return response;
    }

    public Map<String, Object> updateProfile(Long userId, ApiRequests.ProfileUpdate request) {
        AppUser user = getUser(userId);
        if (!isBlank(request.fullName())) {
            user.setFullName(request.fullName());
        }
        if (!isBlank(request.phone())) {
            user.setPhone(request.phone());
        }
        user.setAddress(request.address());
        user.setAvatarUrl(request.avatarUrl());
        return userSummary(user);
    }

    public Map<String, Object> updateRestriction(Long userId, ApiRequests.RestrictionUpdate request) {
        AppUser user = getUser(userId);
        user.setBookingRestricted(request.bookingRestricted());
        user.setRestrictionReason(request.bookingRestricted() ? request.restrictionReason() : null);
        return userSummary(user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users(String roleName) {
        return userRepository.findAll().stream()
                .filter(user -> isBlank(roleName) || user.getRole().getRoleName().equalsIgnoreCase(roleName))
                .map(this::userSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fields() {
        return fieldRepository.findByStatus(CommonStatus.active).stream()
                .map(this::fieldSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fieldDetail(Long fieldId) {
        FootballField field = getField(fieldId);
        List<Map<String, Object>> prices = fieldPriceRepository.findByField_FieldId(fieldId).stream()
                .map(price -> Map.<String, Object>of(
                        "dayType", price.getDayType(),
                        "startTime", price.getStartTime().toString(),
                        "endTime", price.getEndTime().toString(),
                        "price", price.getPrice()
                ))
                .toList();
        Map<String, Object> detail = new LinkedHashMap<>(fieldSummary(field));
        detail.put("prices", prices);
        detail.put("note", "Review text was intentionally removed from MVP schema; field detail shows pricing, services, and availability.");
        return detail;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fieldTypes() {
        return fieldTypeRepository.findAll().stream()
                .map(type -> Map.<String, Object>of(
                        "fieldTypeId", type.getFieldTypeId(),
                        "typeName", type.getTypeName(),
                        "playerCapacity", nvl(type.getPlayerCapacity(), 0)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchSlots(LocalDate date, Long fieldTypeId) {
        LocalDate targetDate = date == null ? LocalDate.now().plusDays(1) : date;
        return slotRepository.findBySlotDate(targetDate).stream()
                .filter(slot -> fieldTypeId == null || Objects.equals(slot.getField().getFieldType().getFieldTypeId(), fieldTypeId))
                .map(slot -> {
                    boolean reserved = bookingRepository.existsBySlotAndStatusIn(slot, ACTIVE_BOOKING_STATUSES);
                    BigDecimal fieldPrice = calculateFieldPrice(slot);
                    return Map.<String, Object>ofEntries(
                            Map.entry("slotId", slot.getSlotId()),
                            Map.entry("fieldId", slot.getField().getFieldId()),
                            Map.entry("fieldName", slot.getField().getFieldName()),
                            Map.entry("fieldType", slot.getField().getFieldType().getTypeName()),
                            Map.entry("slotDate", slot.getSlotDate().toString()),
                            Map.entry("startTime", slot.getStartTime().toString()),
                            Map.entry("endTime", slot.getEndTime().toString()),
                            Map.entry("status", reserved ? "booked" : slot.getStatus().name()),
                            Map.entry("available", slot.getStatus() == SlotStatus.available && !reserved),
                            Map.entry("price", fieldPrice)
                    );
                })
                .toList();
    }

    public Map<String, Object> blockSlot(ApiRequests.SlotBlock request) {
        FootballField field = getField(request.fieldId());
        Slot slot = new Slot();
        slot.setField(field);
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(LocalTime.parse(request.startTime()));
        slot.setEndTime(LocalTime.parse(request.endTime()));
        slot.setStatus(SlotStatus.blocked);
        slot.setBlockReason(request.blockReason());
        slot.setBlockNote(request.blockNote());
        if (request.createdById() != null) {
            slot.setCreatedBy(getUser(request.createdById()));
        }
        return slotSummary(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> services() {
        return extraServiceRepository.findByStatus(CommonStatus.active).stream()
                .map(this::serviceSummary)
                .toList();
    }

    public Map<String, Object> createIssue(ApiRequests.IssueCreate request) {
        requireText(request.title(), "Issue title is required");
        Issue issue = new Issue();
        issue.setReporter(getUser(request.reporterId()));
        if (request.bookingId() != null) {
            issue.setBooking(getBooking(request.bookingId()));
        }
        if (request.fieldId() != null) {
            issue.setField(getField(request.fieldId()));
        }
        if (request.extraServiceId() != null) {
            issue.setExtraService(getExtraService(request.extraServiceId()));
        }
        if (request.assignedStaffId() != null) {
            issue.setAssignedStaff(getUser(request.assignedStaffId()));
        }
        issue.setTitle(request.title());
        issue.setDescription(request.description());
        return issueSummary(issueRepository.save(issue));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issues() {
        return issueRepository.findAllByOrderByIssueIdDesc().stream()
                .map(this::issueSummary)
                .toList();
    }

    public Map<String, Object> previewCheckout(ApiRequests.PromotionApply request) {
        AppUser customer = request.customerId() == null ? null : getUser(request.customerId());
        BookingSource source = parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        Slot slot = getSlot(request.slotId());
        BigDecimal fieldPrice = calculateFieldPrice(slot);
        BigDecimal serviceTotal = calculateServiceTotal(request.services());
        BigDecimal promotionDiscount = calculatePromotionDiscount(request.promotionCode(), slot, serviceTotal.add(fieldPrice), request.services());
        BigDecimal membershipDiscount = calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source);
        BigDecimal total = money(fieldPrice.add(serviceTotal).subtract(promotionDiscount).subtract(membershipDiscount));
        BigDecimal deposit = calculateDeposit(total);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fieldPriceAmount", fieldPrice);
        response.put("serviceTotalAmount", serviceTotal);
        response.put("promotionDiscountAmount", promotionDiscount);
        response.put("membershipDiscountAmount", membershipDiscount);
        response.put("totalAmount", total);
        response.put("depositAmount", deposit);
        response.put("remainingAfterDeposit", money(total.subtract(deposit)));
        response.put("promotionCode", request.promotionCode());
        response.put("bookingSource", source.name());
        response.put("customer", customer == null ? null : userSummary(customer));
        response.put("slot", slotSummary(slot));
        return response;
    }

    public Map<String, Object> createBooking(ApiRequests.BookingCreate request) {
        AppUser customer = getUser(request.customerId());
        if (customer.isBookingRestricted()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer is restricted from creating bookings");
        }
        BookingSource source = parseEnum(BookingSource.class, request.bookingSource(), BookingSource.online);
        if (source == BookingSource.online && !customer.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify customer email before online booking");
        }
        Slot slot = getSlot(request.slotId());
        validateSlotBookable(slot);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        if (request.staffId() != null) {
            booking.setStaff(getUser(request.staffId()));
        }
        booking.setSlot(slot);
        booking.setBookingCode("BK" + System.currentTimeMillis());
        booking.setBookingSource(source);
        booking.setStatus(source == BookingSource.walk_in ? BookingStatus.confirmed : BookingStatus.pending);
        booking.setNote(request.note());

        BigDecimal fieldPrice = calculateFieldPrice(slot);
        BigDecimal serviceTotal = calculateServiceTotal(request.services());
        BigDecimal promotionDiscount = calculatePromotionDiscount(request.promotionCode(), slot, fieldPrice.add(serviceTotal), request.services());
        BigDecimal membershipDiscount = calculateMembershipDiscount(customer, fieldPrice.add(serviceTotal).subtract(promotionDiscount), source);
        BigDecimal total = money(fieldPrice.add(serviceTotal).subtract(promotionDiscount).subtract(membershipDiscount));
        BigDecimal deposit = calculateDeposit(total);

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
        booking = bookingRepository.save(booking);
        saveBookingServices(booking, request.services());
        saveBookingPromotion(booking, request.promotionCode(), promotionDiscount);
        notifyUser(customer, booking, NotificationType.booking_confirmation, "Booking created", "Your booking " + booking.getBookingCode() + " has been created.");
        return bookingDetail(booking.getBookingId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookings(Long customerId, LocalDate date) {
        List<Booking> source;
        if (customerId != null) {
            source = bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(customerId);
        } else if (date != null) {
            source = bookingRepository.findBySlot_SlotDateOrderBySlot_StartTimeAsc(date);
        } else {
            source = bookingRepository.findAll().stream()
                    .sorted(Comparator.comparing(Booking::getBookingId).reversed())
                    .toList();
        }
        return source.stream().map(this::bookingSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> bookingDetail(Long bookingId) {
        Booking booking = getBooking(bookingId);
        Map<String, Object> detail = new LinkedHashMap<>(bookingSummary(booking));
        detail.put("services", bookingServiceItemRepository.findByBooking_BookingId(bookingId).stream().map(this::bookingServiceSummary).toList());
        detail.put("payments", paymentRepository.findByBooking_BookingIdOrderByPaymentIdDesc(bookingId).stream().map(this::paymentSummary).toList());
        detail.put("promotions", bookingPromotionRepository.findByBooking_BookingId(bookingId).stream().map(this::bookingPromotionSummary).toList());
        detail.put("invoice", invoiceRepository.findByBooking_BookingId(bookingId).map(this::invoiceSummary).orElse(null));
        detail.put("refunds", refundRepository.findByBooking_BookingIdOrderByRefundIdDesc(bookingId).stream().map(this::refundSummary).toList());
        return detail;
    }

    public Map<String, Object> updateBookingStatus(Long bookingId, ApiRequests.BookingStatusUpdate request) {
        Booking booking = getBooking(bookingId);
        BookingStatus nextStatus = parseEnum(BookingStatus.class, request.status(), booking.getStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (nextStatus) {
            case confirmed -> {
                booking.setConfirmedAt(now);
                notifyUser(booking.getCustomer(), booking, NotificationType.booking_confirmation, "Booking confirmed", "Booking " + booking.getBookingCode() + " is confirmed.");
            }
            case checked_in -> {
                requireCurrentStatus(booking, BookingStatus.confirmed);
                booking.setCheckedInAt(now);
            }
            case completed -> {
                requireCurrentStatus(booking, BookingStatus.checked_in);
                booking.setCompletedAt(now);
                updateMembershipProgress(booking.getCustomer());
                generateInvoice(booking);
            }
            case cancelled -> {
                if (booking.getStatus() == BookingStatus.checked_in || booking.getStatus() == BookingStatus.completed) {
                    throw badRequest("Checked-in or completed bookings cannot be cancelled");
                }
                booking.setCancelledAt(now);
                previewAndStoreCancellation(booking);
                notifyUser(booking.getCustomer(), booking, NotificationType.cancellation, "Booking cancelled", "Booking " + booking.getBookingCode() + " has been cancelled.");
            }
            case no_show -> {
                requireCurrentStatus(booking, BookingStatus.confirmed);
            }
            case expired -> booking.setExpiredAt(now);
            default -> {
            }
        }
        if (request.staffId() != null) {
            booking.setStaff(getUser(request.staffId()));
        }
        if (!isBlank(request.note())) {
            booking.setNote(request.note());
        }
        booking.setStatus(nextStatus);
        return bookingDetail(bookingId);
    }

    public Map<String, Object> capturePayment(ApiRequests.PaymentCapture request) {
        Booking booking = getBooking(request.bookingId());
        BigDecimal amount = request.amount() == null ? booking.getDepositAmount() : money(request.amount());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentCode("PAY" + System.currentTimeMillis());
        payment.setPaymentOption(parseEnum(PaymentOption.class, request.paymentOption(), PaymentOption.deposit));
        payment.setPaymentMethod(parseEnum(PaymentMethod.class, request.paymentMethod(), PaymentMethod.online_sandbox));
        payment.setAmount(amount);
        payment.setCreatedBy(request.createdById() == null ? booking.getCustomer() : getUser(request.createdById()));
        payment.setStatus(request.success() ? PaymentStatus.paid : PaymentStatus.failed);
        payment.setGatewayMessage(request.success() ? "Sandbox payment accepted" : "Sandbox payment failed");
        payment.setTransactionCode("SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        if (request.success()) {
            payment.setPaidAt(LocalDateTime.now());
            booking.setPaidAmount(money(booking.getPaidAmount().add(amount)));
            booking.setRemainingAmount(money(booking.getTotalAmount().subtract(booking.getPaidAmount()).max(BigDecimal.ZERO)));
            if (booking.getStatus() == BookingStatus.pending && booking.getPaidAmount().compareTo(booking.getDepositAmount()) >= 0) {
                booking.setStatus(BookingStatus.confirmed);
                booking.setConfirmedAt(LocalDateTime.now());
            }
            notifyUser(booking.getCustomer(), booking, NotificationType.payment, "Payment captured", "Payment was captured for " + booking.getBookingCode() + ".");
        }
        paymentRepository.save(payment);
        generateInvoice(booking);
        return bookingDetail(booking.getBookingId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> payments() {
        return paymentRepository.findAllByOrderByPaymentIdDesc().stream().map(this::paymentSummary).toList();
    }

    public Map<String, Object> createRefund(ApiRequests.RefundCreate request) {
        Booking booking = getBooking(request.bookingId());
        Refund refund = new Refund();
        refund.setBooking(booking);
        if (request.paymentId() != null) {
            refund.setPayment(paymentRepository.findById(request.paymentId()).orElseThrow(() -> notFound("Payment not found")));
        }
        refund.setRefundCode("RF" + System.currentTimeMillis());
        refund.setRequestedBy(request.requestedById() == null ? booking.getCustomer() : getUser(request.requestedById()));
        if (request.processedById() != null) {
            refund.setProcessedBy(getUser(request.processedById()));
        }
        refund.setRefundAmount(request.refundAmount() == null ? booking.getRefundableAmount() : money(request.refundAmount()));
        refund.setRefundReason(request.refundReason());
        refund.setRequestedAt(LocalDateTime.now());
        if (request.approveNow()) {
            refund.setStatus(RefundStatus.completed);
            refund.setProcessedAt(LocalDateTime.now());
            refund.setTransactionCode("REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            notifyUser(booking.getCustomer(), booking, NotificationType.refund, "Refund completed", "Refund has been completed for " + booking.getBookingCode() + ".");
        }
        Refund savedRefund = refundRepository.save(refund);
        invoiceRepository.findByBooking_BookingId(booking.getBookingId()).ifPresent(invoice -> invoice.setRefundAmount(savedRefund.getRefundAmount()));
        return refundSummary(savedRefund);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> refunds() {
        return refundRepository.findAllByOrderByRefundIdDesc().stream().map(this::refundSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> promotions() {
        return promotionRepository.findByStatus(CommonStatus.active).stream().map(this::promotionSummary).toList();
    }

    public Map<String, Object> applyPromotionPreview(ApiRequests.PromotionApply request) {
        return previewCheckout(request);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> membershipProgress(Long customerId) {
        AppUser customer = getUser(customerId);
        CustomerMembership membership = customerMembershipRepository.findByCustomer_UserId(customerId)
                .orElseThrow(() -> notFound("Membership not found"));
        List<MembershipLevel> levels = membershipLevelRepository.findAllByOrderByDisplayOrderAsc();
        MembershipLevel next = levels.stream()
                .filter(level -> level.getRequiredCompletedBookings() > membership.getCompletedBookingCount())
                .findFirst()
                .orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", userSummary(customer));
        response.put("currentLevel", membership.getMembershipLevel().getLevelName());
        response.put("completedBookingCount", membership.getCompletedBookingCount());
        response.put("discountPercent", membership.getMembershipLevel().getDiscountPercent());
        response.put("nextLevel", next == null ? null : next.getLevelName());
        response.put("bookingsToNextLevel", next == null ? 0 : next.getRequiredCompletedBookings() - membership.getCompletedBookingCount());
        response.put("levels", levels.stream().map(this::membershipLevelSummary).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> membershipLevels() {
        return membershipLevelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::membershipLevelSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reports() {
        List<Booking> bookings = bookingRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
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
        response.put("totalRevenue", money(revenue));
        response.put("bookingCount", bookings.size());
        response.put("completedCount", completed);
        response.put("cancelledCount", cancelled);
        response.put("noShowCount", noShow);
        response.put("fieldUtilization", byField);
        response.put("topCustomers", userRepository.findAll().stream()
                .filter(user -> "Customer".equals(user.getRole().getRoleName()))
                .map(user -> Map.<String, Object>of(
                        "customerId", user.getUserId(),
                        "fullName", user.getFullName(),
                        "bookingCount", bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(user.getUserId()).size()
                ))
                .toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> settings(String group) {
        List<SystemSetting> settings = isBlank(group)
                ? systemSettingRepository.findAll()
                : systemSettingRepository.findBySettingGroupOrderBySettingKeyAsc(group);
        return settings.stream().map(this::settingSummary).toList();
    }

    public Map<String, Object> updateSetting(String key, ApiRequests.SettingUpdate request) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> notFound("Setting not found"));
        setting.setSettingValue(request.settingValue());
        if (request.updatedById() != null) {
            setting.setUpdatedBy(getUser(request.updatedById()));
        }
        return settingSummary(setting);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> notifications(Long userId) {
        return notificationRepository.findByUser_UserIdOrderByNotificationIdDesc(userId).stream()
                .map(this::notificationSummary)
                .toList();
    }

    private void validateSlotBookable(Slot slot) {
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

    private BigDecimal calculateFieldPrice(Slot slot) {
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

    private BigDecimal calculateServiceTotal(List<ApiRequests.ServiceSelection> selections) {
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

    private BigDecimal calculatePromotionDiscount(String promotionCode, Slot slot, BigDecimal baseAmount, List<ApiRequests.ServiceSelection> services) {
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

    private BigDecimal calculateMembershipDiscount(AppUser customer, BigDecimal baseAmount, BookingSource source) {
        if (customer == null || source != BookingSource.online) {
            return BigDecimal.ZERO;
        }
        return customerMembershipRepository.findByCustomer_UserId(customer.getUserId())
                .map(membership -> money(baseAmount.multiply(membership.getMembershipLevel().getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculateDeposit(BigDecimal total) {
        BigDecimal percent = systemSettingRepository.findBySettingKey("deposit.default_percent")
                .map(SystemSetting::getSettingValue)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(30));
        return money(total.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    private void saveBookingServices(Booking booking, List<ApiRequests.ServiceSelection> selections) {
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

    private int validateServiceQuantity(ExtraService service, Integer requestedQuantity) {
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

    private void saveBookingPromotion(Booking booking, String promotionCode, BigDecimal promotionDiscount) {
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

    private void generateInvoice(Booking booking) {
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

    private void previewAndStoreCancellation(Booking booking) {
        BigDecimal paid = booking.getPaidAmount();
        BigDecimal fee = paid.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundable = paid.subtract(fee).max(BigDecimal.ZERO);
        booking.setCancellationFeeAmount(fee);
        booking.setRefundableAmount(refundable);
    }

    private void updateMembershipProgress(AppUser customer) {
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

    private CustomerMembership attachDefaultMembership(AppUser customer) {
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

    private void notifyUser(AppUser user, Booking booking, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setBooking(booking);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private String issueEmailVerification(AppUser user) {
        String token = String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 900000));
        user.setEmailVerificationToken(token);
        user.setEmailVerificationSentAt(LocalDateTime.now());
        if (user.getUserId() != null) {
            notifyUser(user, null, NotificationType.system, "Verify email", "Use code " + token + " to verify your email.");
        }
        return token;
    }

    private Map<String, Object> userSummary(AppUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", user.getUserId());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("role", user.getRole().getRoleName());
        map.put("status", user.getStatus().name());
        map.put("bookingRestricted", user.isBookingRestricted());
        map.put("emailVerified", user.isEmailVerified());
        map.put("restrictionReason", user.getRestrictionReason());
        map.put("lastLoginAt", user.getLastLoginAt());
        return map;
    }

    private Map<String, Object> fieldSummary(FootballField field) {
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

    private Map<String, Object> slotSummary(Slot slot) {
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

    private Map<String, Object> serviceSummary(ExtraService service) {
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

    private Map<String, Object> issueSummary(Issue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getIssueId());
        map.put("title", issue.getTitle());
        map.put("description", issue.getDescription());
        map.put("status", issue.getStatus().name());
        map.put("reporter", issue.getReporter().getFullName());
        map.put("bookingCode", issue.getBooking() == null ? null : issue.getBooking().getBookingCode());
        map.put("fieldName", issue.getField() == null ? null : issue.getField().getFieldName());
        map.put("extraServiceName", issue.getExtraService() == null ? null : issue.getExtraService().getServiceName());
        return map;
    }

    private Map<String, Object> bookingSummary(Booking booking) {
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

    private Map<String, Object> bookingServiceSummary(BookingServiceItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceName", item.getExtraService().getServiceName());
        map.put("quantity", item.getQuantity());
        map.put("unitPrice", item.getUnitPrice());
        map.put("lineTotal", item.getLineTotal());
        return map;
    }

    private Map<String, Object> paymentSummary(Payment payment) {
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

    private Map<String, Object> invoiceSummary(Invoice invoice) {
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

    private Map<String, Object> refundSummary(Refund refund) {
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

    private Map<String, Object> promotionSummary(Promotion promotion) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promotionId", promotion.getPromotionId());
        map.put("promotionCode", promotion.getPromotionCode());
        map.put("promotionName", promotion.getPromotionName());
        map.put("description", promotion.getDescription());
        map.put("discountType", promotion.getDiscountType().name());
        map.put("discountValue", promotion.getDiscountValue());
        map.put("maxDiscountAmount", promotion.getMaxDiscountAmount());
        map.put("minBookingAmount", promotion.getMinBookingAmount());
        map.put("startDate", promotion.getStartDate().toString());
        map.put("endDate", promotion.getEndDate().toString());
        map.put("usedCount", promotion.getUsedCount());
        return map;
    }

    private Map<String, Object> bookingPromotionSummary(BookingPromotion bookingPromotion) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promotionCode", bookingPromotion.getPromotionCodeSnapshot());
        map.put("discountAmount", bookingPromotion.getDiscountAmount());
        map.put("appliedAt", bookingPromotion.getAppliedAt());
        return map;
    }

    private Map<String, Object> membershipLevelSummary(MembershipLevel level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("membershipLevelId", level.getMembershipLevelId());
        map.put("levelName", level.getLevelName());
        map.put("requiredCompletedBookings", level.getRequiredCompletedBookings());
        map.put("discountPercent", level.getDiscountPercent());
        map.put("benefitDescription", level.getBenefitDescription());
        return map;
    }

    private Map<String, Object> settingSummary(SystemSetting setting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("settingKey", setting.getSettingKey());
        map.put("settingValue", setting.getSettingValue());
        map.put("settingGroup", setting.getSettingGroup());
        map.put("description", setting.getDescription());
        return map;
    }

    private Map<String, Object> notificationSummary(Notification notification) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("notificationId", notification.getNotificationId());
        map.put("title", notification.getTitle());
        map.put("message", notification.getMessage());
        map.put("type", notification.getNotificationType().name());
        map.put("read", notification.isRead());
        map.put("sentAt", notification.getSentAt());
        return map;
    }

    private AppUser getUser(Long userId) {
        if (userId == null) {
            throw badRequest("User id is required");
        }
        return userRepository.findById(userId).orElseThrow(() -> notFound("User not found"));
    }

    private FootballField getField(Long fieldId) {
        if (fieldId == null) {
            throw badRequest("Field id is required");
        }
        return fieldRepository.findById(fieldId).orElseThrow(() -> notFound("Field not found"));
    }

    private Slot getSlot(Long slotId) {
        if (slotId == null) {
            throw badRequest("Slot id is required");
        }
        return slotRepository.findById(slotId).orElseThrow(() -> notFound("Slot not found"));
    }

    private Booking getBooking(Long bookingId) {
        if (bookingId == null) {
            throw badRequest("Booking id is required");
        }
        return bookingRepository.findById(bookingId).orElseThrow(() -> notFound("Booking not found"));
    }

    private ExtraService getExtraService(Long serviceId) {
        if (serviceId == null) {
            throw badRequest("Service id is required");
        }
        return extraServiceRepository.findById(serviceId).orElseThrow(() -> notFound("Extra service not found"));
    }

    private void requireCurrentStatus(Booking booking, BookingStatus status) {
        if (booking.getStatus() != status) {
            throw badRequest("Booking must be " + status + " before this action");
        }
    }

    private void requireText(String value, String message) {
        if (isBlank(value)) {
            throw badRequest(message);
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private ApiException serverError(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private <T> T nvl(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T fallback) {
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
