package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Role;
import com.swp391.backend.enums.AccountStatus;
import com.swp391.backend.security.JwtService;
import com.swp391.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class AccountService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");
    private static final String PASSWORD_POLICY_MESSAGE = "Password must be at least 8 characters and include uppercase, lowercase, number, and special character.";

    private final DemoSupportService support;
    private final VerificationEmailService verificationEmailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(DemoSupportService support, VerificationEmailService verificationEmailService, BCryptPasswordEncoder passwordEncoder, JwtService jwtService) {
        this.support = support;
        this.verificationEmailService = verificationEmailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(ApiRequests.Register request) {
        support.requireText(request.fullName(), "Full name is required");
        String email = support.clean(request.email());
        String phone = support.clean(request.phone());
        support.requireText(email, "Email is required");
        support.requireText(phone, "Phone is required");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw support.badRequest("Email format is invalid");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw support.badRequest("Phone must be 10 digits and start with 0");
        }
        validatePassword(request.password(), request.confirmPassword());
        if (support.userRepository.findByEmail(email).isPresent()) {
            throw support.badRequest("Email already exists");
        }
        if (support.userRepository.findByPhone(phone).isPresent()) {
            throw support.badRequest("Phone already exists");
        }

        Role customerRole = support.roleRepository.findByRoleName("Customer")
                .orElseThrow(() -> support.serverError("Customer role has not been seeded"));
        AppUser user = new AppUser();
        user.setRole(customerRole);
        user.setFullName(support.clean(request.fullName()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);
        issueEmailVerification(user);
        user = support.userRepository.save(user);
        VerificationEmailDelivery delivery = verificationEmailService.sendVerificationEmail(user);
        support.attachDefaultMembership(user);

        Map<String, Object> response = new LinkedHashMap<>();
        String token = jwtService.generateToken(new SecurityUser(user));
        response.put("token", token);
        response.put("user", support.userSummary(user));
        response.put("verificationRequired", !user.isEmailVerified());
        response.put("emailDeliveryStatus", delivery.status());
        response.put("message", "Account created. " + delivery.message());
        return response;
    }

    public Map<String, Object> login(ApiRequests.Login request) {
        support.requireText(request.emailOrPhone(), "Email or phone is required");
        support.requireText(request.password(), "Password is required");
        String identity = support.clean(request.emailOrPhone());
        AppUser user = support.userRepository.findByEmail(identity)
                .or(() -> support.userRepository.findByPhone(identity))
                .orElseThrow(() -> support.notFound("Account not found"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getStatus() != AccountStatus.active) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        user.setLastLoginAt(LocalDateTime.now());
        String token = jwtService.generateToken(new SecurityUser(user));
        return Map.of(
                "token", token,
                "user", support.userSummary(user)
        );
    }

    public Map<String, Object> verifyEmail(ApiRequests.EmailVerification request) {
        AppUser user = support.getUser(request.userId());
        if (user.isEmailVerified()) {
            return Map.of("user", support.userSummary(user), "message", "Email is already verified");
        }
        support.requireText(request.token(), "Verification token is required");
        if (!Objects.equals(user.getEmailVerificationToken(), request.token().trim())) {
            throw support.badRequest("Invalid or expired verification link");
        }
        if (user.getEmailVerificationSentAt() == null
                || Duration.between(user.getEmailVerificationSentAt(), LocalDateTime.now()).toHours() >= 1) {
            throw support.badRequest("Verification link has expired. Request a new one.");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationSentAt(null);
        return Map.of("user", support.userSummary(user), "message", "Email verified");
    }

    public Map<String, Object> resendEmailVerification(ApiRequests.EmailVerificationResend request) {
        AppUser user = support.getUser(request.userId());
        if (support.isBlank(user.getEmail())) {
            throw support.badRequest("Account does not have an email address");
        }
        if (user.isEmailVerified()) {
            return Map.of("user", support.userSummary(user), "message", "Email is already verified");
        }
        issueEmailVerification(user);
        VerificationEmailDelivery delivery = verificationEmailService.sendVerificationEmail(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", support.userSummary(user));
        response.put("emailDeliveryStatus", delivery.status());
        response.put("message", delivery.message());
        return response;
    }

    public Map<String, Object> updateProfile(Long userId, ApiRequests.ProfileUpdate request) {
        requireSelfOrAdmin(userId);
        AppUser user = support.getUser(userId);
        String fullName = support.clean(request.fullName());
        String phone = support.clean(request.phone());
        if (!support.isBlank(fullName)) {
            user.setFullName(fullName);
        }
        if (!support.isBlank(phone)) {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw support.badRequest("Phone must be 10 digits and start with 0");
            }
            support.userRepository.findByPhone(phone)
                    .filter(existing -> !Objects.equals(existing.getUserId(), userId))
                    .ifPresent(existing -> {
                        throw support.badRequest("Phone already exists");
                    });
            user.setPhone(phone);
        }
        user.setAddress(support.clean(request.address()));
        user.setAvatarUrl(support.clean(request.avatarUrl()));
        return support.userSummary(user);
    }

    public Map<String, Object> validateResetToken(ApiRequests.ValidateResetToken request) {
        AppUser user = support.getUser(request.userId());
        if (!Objects.equals(user.getPasswordResetToken(), request.token().trim())) {
            throw support.badRequest("Invalid or expired reset link");
        }
        if (user.getPasswordResetSentAt() == null
                || Duration.between(user.getPasswordResetSentAt(), LocalDateTime.now()).toHours() >= 1) {
            throw support.badRequest("Reset link has expired. Request a new one.");
        }
        return Map.of("valid", true);
    }

    public Map<String, Object> forgotPassword(ApiRequests.ForgotPassword request) {
        String email = support.clean(request.email());
        support.requireText(email, "Email is required");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw support.badRequest("Email format is invalid");
        }

        AppUser user = support.userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Map.of("message", "If this email is registered, a password reset link has been sent.");
        }
        if (user.getStatus() != AccountStatus.active) {
            return Map.of("message", "If this email is registered, a password reset link has been sent.");
        }

        issuePasswordReset(user);
        support.userRepository.save(user);
        verificationEmailService.sendPasswordResetEmail(user);
        return Map.of("message", "If this email is registered, a password reset link has been sent.");
    }

    public Map<String, Object> resetPassword(ApiRequests.ResetPassword request) {
        AppUser user = support.getUser(request.userId());
        support.requireText(request.token(), "Reset token is required");
        if (!Objects.equals(user.getPasswordResetToken(), request.token().trim())) {
            throw support.badRequest("Invalid or expired reset link");
        }
        if (user.getPasswordResetSentAt() == null
                || Duration.between(user.getPasswordResetSentAt(), LocalDateTime.now()).toHours() >= 1) {
            throw support.badRequest("Reset link has expired. Request a new one.");
        }
        validatePassword(request.newPassword(), request.confirmPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetSentAt(null);
        revokeAllTokens(user);
        return Map.of("message", "Password reset successfully");
    }

    // Backlog owner: BonVT - UC-05 Change password.
    public Map<String, Object> changePassword(Long userId, ApiRequests.PasswordChange request) {
        requireSelfOrAdmin(userId);
        AppUser user = support.getUser(userId);
        support.requireText(request.currentPassword(), "Current password is required");
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        validatePassword(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw support.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        revokeAllTokens(user);
        return Map.of(
                "user", support.userSummary(user),
                "message", "Password changed successfully"
        );
    }

    public Map<String, Object> updateRestriction(Long userId, ApiRequests.RestrictionUpdate request) {
        requireAdmin();
        AppUser user = support.getUser(userId);
        if (!"Customer".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw support.badRequest("Only customer accounts can be restricted for booking");
        }
        VerificationEmailDelivery delivery = null;
        user.setBookingRestricted(request.bookingRestricted());
        revokeAllTokens(user);
        String reason = support.clean(request.restrictionReason());
        if (request.bookingRestricted()) {
            support.requireText(reason, "Restriction reason is required");
            user.setRestrictionReason(reason);
            if (!support.isBlank(user.getEmail())) {
                delivery = verificationEmailService.sendRestrictionEmail(user, reason);
            }
        } else {
            user.setRestrictionReason(null);
        }

        Map<String, Object> response = new LinkedHashMap<>(support.userSummary(user));
        if (delivery != null) {
            response.put("emailDeliveryStatus", delivery.status());
            response.put("message", delivery.message());
        } else {
            response.put("message", request.bookingRestricted() ? "Customer restricted." : "Customer booking access restored.");
        }
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> users(String roleName) {
        requireAdmin();
        return support.userRepository.findAll().stream()
                .filter(user -> support.isBlank(roleName) || user.getRole().getRoleName().equalsIgnoreCase(roleName))
                .map(support::userSummary)
                .toList();
    }

    /** Staff need to identify a customer before opening a walk-in booking, without gaining account-management rights. */
    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> walkInCustomers() {
        AppUser requester = currentUser();
        String role = requester.getRole().getRoleName();
        if (!"Staff".equalsIgnoreCase(role) && !"Admin".equalsIgnoreCase(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff or administrator access is required");
        }
        return support.userRepository.findAll().stream()
                .filter(user -> "Customer".equalsIgnoreCase(user.getRole().getRoleName()))
                .filter(user -> user.getStatus() == AccountStatus.active)
                .map(support::userSummary)
                .toList();
    }

    public Map<String, Object> logout() {
        AppUser user = currentUser();
        revokeAllTokens(user);
        return Map.of("message", "Signed out. This token has been revoked.");
    }

    public Map<String, Object> updateStatus(Long userId, ApiRequests.AccountStatusUpdate request) {
        requireAdmin();
        AppUser user = support.getUser(userId);
        if (Objects.equals(user.getUserId(), currentUser().getUserId())) {
            throw support.badRequest("Administrators cannot lock their own account");
        }
        AccountStatus status = support.parseEnum(AccountStatus.class, request.status(), user.getStatus());
        user.setStatus(status);
        revokeAllTokens(user);
        return support.userSummary(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> activity(Long userId) {
        requireSelfOrOperator(userId);
        AppUser user = support.getUser(userId);
        var bookings = support.bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(userId).stream()
                .limit(20)
                .map(support::bookingSummary)
                .toList();
        var issues = support.issueRepository.findAllByOrderByIssueIdDesc().stream()
                .filter(issue -> Objects.equals(issue.getReporter().getUserId(), userId))
                .limit(20)
                .map(support::issueSummary)
                .toList();
        long completedBookings = support.bookingRepository.findByCustomer_UserIdOrderByBookingIdDesc(userId).stream()
                .filter(booking -> booking.getStatus() == com.swp391.backend.enums.BookingStatus.completed)
                .count();
        return Map.of(
                "user", support.userSummary(user),
                "bookingCount", bookings.size(),
                "completedBookingCount", completedBookings,
                "bookings", bookings,
                "issues", issues
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> staff() {
        requireAdmin();
        return support.userRepository.findAll().stream()
                .filter(user -> "Staff".equalsIgnoreCase(user.getRole().getRoleName()))
                .map(support::userSummary)
                .toList();
    }

    public Map<String, Object> createStaff(ApiRequests.StaffUpsert request) {
        requireAdmin();
        validateStaffRequest(request, true, null);
        Role staffRole = support.roleRepository.findByRoleName("Staff")
                .orElseThrow(() -> support.serverError("Staff role has not been seeded"));
        AppUser staff = new AppUser();
        staff.setRole(staffRole);
        applyStaffRequest(staff, request, true);
        return support.userSummary(support.userRepository.save(staff));
    }

    public Map<String, Object> updateStaff(Long userId, ApiRequests.StaffUpsert request) {
        requireAdmin();
        AppUser staff = support.getUser(userId);
        if (!"Staff".equalsIgnoreCase(staff.getRole().getRoleName())) {
            throw support.badRequest("The selected account is not a staff account");
        }
        validateStaffRequest(request, false, userId);
        applyStaffRequest(staff, request, false);
        if (!support.isBlank(request.password())) {
            revokeAllTokens(staff);
        }
        if (staff.getStatus() != AccountStatus.active) {
            revokeAllTokens(staff);
        }
        return support.userSummary(staff);
    }

    private void validateStaffRequest(ApiRequests.StaffUpsert request, boolean creating, Long currentUserId) {
        support.requireText(request.fullName(), "Full name is required");
        String email = support.clean(request.email());
        String phone = support.clean(request.phone());
        support.requireText(email, "Email is required");
        support.requireText(phone, "Phone is required");
        if (!EMAIL_PATTERN.matcher(email).matches()) throw support.badRequest("Email format is invalid");
        if (!PHONE_PATTERN.matcher(phone).matches()) throw support.badRequest("Phone must be 10 digits and start with 0");
        support.userRepository.findByEmail(email).filter(user -> !Objects.equals(user.getUserId(), currentUserId))
                .ifPresent(user -> { throw support.badRequest("Email already exists"); });
        support.userRepository.findByPhone(phone).filter(user -> !Objects.equals(user.getUserId(), currentUserId))
                .ifPresent(user -> { throw support.badRequest("Phone already exists"); });
        if (creating) {
            validatePassword(request.password(), request.password());
        } else if (!support.isBlank(request.password())) {
            validatePassword(request.password(), request.password());
        }
    }

    private void applyStaffRequest(AppUser staff, ApiRequests.StaffUpsert request, boolean creating) {
        staff.setFullName(support.clean(request.fullName()));
        staff.setEmail(support.clean(request.email()));
        staff.setPhone(support.clean(request.phone()));
        staff.setStatus(support.parseEnum(AccountStatus.class, request.status(), AccountStatus.active));
        if (creating || !support.isBlank(request.password())) {
            staff.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (creating) {
            staff.setEmailVerified(true);
        }
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        return user.getAppUser();
    }

    private void requireAdmin() {
        if (!"Admin".equalsIgnoreCase(currentUser().getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
    }

    private void requireSelfOrAdmin(Long userId) {
        AppUser requester = currentUser();
        if (!Objects.equals(requester.getUserId(), userId) && !"Admin".equalsIgnoreCase(requester.getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only access your own account");
        }
    }

    /** Staff need a concise customer history while handling bookings and support cases (UC-09). */
    private void requireSelfOrOperator(Long userId) {
        AppUser requester = currentUser();
        String role = requester.getRole().getRoleName();
        boolean operator = "Staff".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
        if (!Objects.equals(requester.getUserId(), userId) && !operator) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only access your own account");
        }
    }

    private void revokeAllTokens(AppUser user) {
        user.setAuthVersion(user.getAuthVersion() + 1);
    }

    private void issueEmailVerification(AppUser user) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        user.setEmailVerificationToken(token);
        user.setEmailVerificationSentAt(LocalDateTime.now());
    }

    private void issuePasswordReset(AppUser user) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        user.setPasswordResetToken(token);
        user.setPasswordResetSentAt(LocalDateTime.now());
    }

    private void validatePassword(String password, String confirmPassword) {
        support.requireText(password, "Password is required");
        if (password.length() < 8 || password.length() > 72
                || password.chars().anyMatch(Character::isWhitespace)
                || !UPPERCASE_PATTERN.matcher(password).matches()
                || !LOWERCASE_PATTERN.matcher(password).matches()
                || !NUMBER_PATTERN.matcher(password).matches()
                || !SPECIAL_PATTERN.matcher(password).matches()) {
            throw support.badRequest(PASSWORD_POLICY_MESSAGE);
        }
        if (!Objects.equals(password, confirmPassword)) {
            throw support.badRequest("Password confirmation does not match");
        }
    }
}
