package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.MvpDemoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final MvpDemoService demoService;

    public AccountController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/register")
    public Object register(@RequestBody ApiRequests.Register request) {
        return demoService.register(request);
    }

    @PostMapping("/login")
    public Object login(@RequestBody ApiRequests.Login request) {
        return demoService.login(request);
    }

    @PostMapping("/email/verify")
    public Object verifyEmail(@RequestBody ApiRequests.EmailVerification request) {
        return demoService.verifyEmail(request);
    }

    @PostMapping("/email/resend")
    public Object resendEmailVerification(@RequestBody ApiRequests.EmailVerificationResend request) {
        return demoService.resendEmailVerification(request);
    }

    @GetMapping("/users")
    public Object users(@RequestParam(required = false) String role) {
        return demoService.users(role);
    }

    @PutMapping("/users/{userId}/profile")
    public Object updateProfile(@PathVariable Long userId, @RequestBody ApiRequests.ProfileUpdate request) {
        return demoService.updateProfile(userId, request);
    }

    @PutMapping("/users/{userId}/restriction")
    public Object updateRestriction(@PathVariable Long userId, @RequestBody ApiRequests.RestrictionUpdate request) {
        return demoService.updateRestriction(userId, request);
    }
}
