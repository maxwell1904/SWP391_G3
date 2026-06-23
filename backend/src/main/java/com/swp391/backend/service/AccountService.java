package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Role;
import com.swp391.backend.enums.AccountStatus;
import org.springframework.http.HttpStatus;
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

    public AccountService(DemoSupportService support, VerificationEmailService verificationEmailService) {
        this.support = support;
        this.verificationEmailService = verificationEmailService;
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
        user.setPasswordHash(request.password());
        user.setEmailVerified(false);
        issueEmailVerification(user);
        user = support.userRepository.save(user);
        VerificationEmailDelivery delivery = verificationEmailService.sendVerificationEmail(user);
        support.attachDefaultMembership(user);

        Map<String, Object> response = new LinkedHashMap<>();
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
        if (!Objects.equals(user.getPasswordHash(), request.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getStatus() != AccountStatus.active) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        user.setLastLoginAt(LocalDateTime.now());
        return Map.of(
                "token", "demo-token-" + user.getUserId(),
                "user", support.userSummary(user),
                "note", "Demo login only. Replace with JWT before production."
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
        user.setPasswordHash(request.newPassword());
        user.setPasswordResetToken(null);
        user.setPasswordResetSentAt(null);
        return Map.of("message", "Password reset successfully");
    }

    // Backlog owner: BonVT - UC-05 Change password.
    public Map<String, Object> changePassword(Long userId, ApiRequests.PasswordChange request) {
        AppUser user = support.getUser(userId);
        support.requireText(request.currentPassword(), "Current password is required");
        if (!Objects.equals(user.getPasswordHash(), request.currentPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        validatePassword(request.newPassword(), request.confirmPassword());
        if (Objects.equals(user.getPasswordHash(), request.newPassword())) {
            throw support.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(request.newPassword());
        return Map.of(
                "user", support.userSummary(user),
                "message", "Password changed successfully"
        );
    }

    public Map<String, Object> updateRestriction(Long userId, ApiRequests.RestrictionUpdate request) {
        AppUser user = support.getUser(userId);
        if (!"Customer".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw support.badRequest("Only customer accounts can be restricted for booking");
        }
        user.setBookingRestricted(request.bookingRestricted());
        String reason = support.clean(request.restrictionReason());
        user.setRestrictionReason(request.bookingRestricted() ? (support.isBlank(reason) ? "Booking restricted by admin" : reason) : null);
        return support.userSummary(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> users(String roleName) {
        return support.userRepository.findAll().stream()
                .filter(user -> support.isBlank(roleName) || user.getRole().getRoleName().equalsIgnoreCase(roleName))
                .map(support::userSummary)
                .toList();
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
