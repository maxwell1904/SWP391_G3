package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.AccountService;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    public Object register(@RequestBody ApiRequests.Register request) {
        return accountService.register(request);
    }

    @PostMapping("/login")
    public Object login(@RequestBody ApiRequests.Login request) {
        return accountService.login(request);
    }

    @PostMapping("/email/verify")
    public Object verifyEmail(@RequestBody ApiRequests.EmailVerification request) {
        return accountService.verifyEmail(request);
    }

    @PostMapping("/email/resend")
    public Object resendEmailVerification(@RequestBody ApiRequests.EmailVerificationResend request) {
        return accountService.resendEmailVerification(request);
    }

    @GetMapping("/users")
    public Object users(@RequestParam(required = false) String role) {
        return accountService.users(role);
    }

    /** A counter-safe customer directory for walk-in bookings; it is not the admin user-management API. */
    @GetMapping("/customers")
    public Object customers() {
        return accountService.walkInCustomers();
    }

    @PutMapping("/users/{userId}/profile")
    public Object updateProfile(@PathVariable Long userId, @RequestBody ApiRequests.ProfileUpdate request) {
        return accountService.updateProfile(userId, request);
    }

    @PutMapping("/users/{userId}/password")
    public Object changePassword(@PathVariable Long userId, @RequestBody ApiRequests.PasswordChange request) {
        return accountService.changePassword(userId, request);
    }

    @PostMapping("/forgot-password")
    public Object forgotPassword(@RequestBody ApiRequests.ForgotPassword request) {
        return accountService.forgotPassword(request);
    }

    @PostMapping("/validate-reset-token")
    public Object validateResetToken(@RequestBody ApiRequests.ValidateResetToken request) {
        return accountService.validateResetToken(request);
    }

    @PostMapping("/reset-password")
    public Object resetPassword(@RequestBody ApiRequests.ResetPassword request) {
        return accountService.resetPassword(request);
    }

    @PutMapping("/users/{userId}/lock")
    public Object updateAccountLock(@PathVariable Long userId, @RequestBody ApiRequests.AccountLockUpdate request) {
        return accountService.updateAccountLock(userId, request);
    }

    @PutMapping("/users/{userId}/status")
    public Object updateStatus(@PathVariable Long userId, @RequestBody ApiRequests.AccountStatusUpdate request) {
        return accountService.updateStatus(userId, request);
    }

    @GetMapping("/users/{userId}/activity")
    public Object activity(@PathVariable Long userId) {
        return accountService.activity(userId);
    }

    @GetMapping("/staff")
    public Object staff() {
        return accountService.staff();
    }

    @PostMapping("/staff")
    public Object createStaff(@RequestBody ApiRequests.StaffUpsert request) {
        return accountService.createStaff(request);
    }

    @PutMapping("/staff/{userId}")
    public Object updateStaff(@PathVariable Long userId, @RequestBody ApiRequests.StaffUpsert request) {
        return accountService.updateStaff(userId, request);
    }
}
