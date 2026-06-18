package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.PayPalCheckoutService;
import com.swp391.backend.service.PaymentWorkflowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentWorkflowService paymentWorkflowService;
    private final PayPalCheckoutService payPalCheckoutService;

    public PaymentController(PaymentWorkflowService paymentWorkflowService, PayPalCheckoutService payPalCheckoutService) {
        this.paymentWorkflowService = paymentWorkflowService;
        this.payPalCheckoutService = payPalCheckoutService;
    }

    @GetMapping("/payments")
    public Object payments() {
        return paymentWorkflowService.payments();
    }

    @PostMapping("/payments/capture")
    public Object capturePayment(@RequestBody ApiRequests.PaymentCapture request) {
        return paymentWorkflowService.capturePayment(request);
    }

    @GetMapping("/payments/paypal/config")
    public Object payPalConfig() {
        return payPalCheckoutService.config();
    }

    @PostMapping("/bookings/{bookingId}/paypal/orders")
    public Object createPayPalOrder(@PathVariable Long bookingId, @RequestBody ApiRequests.PayPalOrderCreate request) {
        return payPalCheckoutService.createOrder(bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/paypal/orders/{orderId}/capture")
    public Object capturePayPalOrder(
            @PathVariable Long bookingId,
            @PathVariable String orderId,
            @RequestBody ApiRequests.PayPalOrderCapture request
    ) {
        return payPalCheckoutService.captureOrder(bookingId, orderId, request);
    }

    @PostMapping("/bookings/{bookingId}/paypal/orders/{orderId}/cancel")
    public Object cancelPayPalOrder(@PathVariable Long bookingId, @PathVariable String orderId) {
        return payPalCheckoutService.cancelOrder(bookingId, orderId);
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
