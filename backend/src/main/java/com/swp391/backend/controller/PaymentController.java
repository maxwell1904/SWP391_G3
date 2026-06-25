package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.PaymentWorkflowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentWorkflowService paymentWorkflowService;

    public PaymentController(PaymentWorkflowService paymentWorkflowService) {
        this.paymentWorkflowService = paymentWorkflowService;
    }

    @GetMapping("/payments")
    public Object payments() {
        return paymentWorkflowService.payments();
    }

    @PostMapping("/payments/capture")
    public Object capturePayment(@RequestBody ApiRequests.PaymentCapture request) {
        return paymentWorkflowService.capturePayment(request);
    }

    @GetMapping("/refunds")
    public Object refunds() {
        return paymentWorkflowService.refunds();
    }

    @PostMapping("/refunds")
    public Object createRefund(@RequestBody ApiRequests.RefundCreate request) {
        return paymentWorkflowService.createRefund(request);
    }
}
