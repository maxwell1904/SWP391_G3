package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.AccountService;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/users/{userId}/profile")
    public Object updateProfile(@PathVariable Long userId, @RequestBody ApiRequests.ProfileUpdate request) {
        return accountService.updateProfile(userId, request);
    }

    @PutMapping("/users/{userId}/password")
    public Object changePassword(@PathVariable Long userId, @RequestBody ApiRequests.PasswordChange request) {
        return accountService.changePassword(userId, request);
    }

    @PutMapping("/users/{userId}/restriction")
    public Object updateRestriction(@PathVariable Long userId, @RequestBody ApiRequests.RestrictionUpdate request) {
        return accountService.updateRestriction(userId, request);
    }
}
