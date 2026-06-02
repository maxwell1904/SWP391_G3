package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.MvpDemoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final MvpDemoService demoService;

    public PaymentController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/payments")
    public Object payments() {
        return demoService.payments();
    }

    @PostMapping("/payments/capture")
    public Object capturePayment(@RequestBody ApiRequests.PaymentCapture request) {
        return demoService.capturePayment(request);
    }

    @GetMapping("/refunds")
    public Object refunds() {
        return demoService.refunds();
    }

    @PostMapping("/refunds")
    public Object createRefund(@RequestBody ApiRequests.RefundCreate request) {
        return demoService.createRefund(request);
    }
}
