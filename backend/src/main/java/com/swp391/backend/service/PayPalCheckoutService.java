package com.swp391.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Payment;
import com.swp391.backend.enums.BookingSource;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.NotificationType;
import com.swp391.backend.enums.PaymentMethod;
import com.swp391.backend.enums.PaymentOption;
import com.swp391.backend.enums.PaymentStatus;
import com.swp391.backend.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PayPalCheckoutService {
    public record RefundResult(String refundId, String status, String message) {
    }

    private final DemoSupportService support;
    private final BookingWorkflowService bookingWorkflowService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.paypal.client-id:}")
    private String clientId;

    @Value("${app.paypal.client-secret:}")
    private String clientSecret;

    @Value("${app.paypal.env:sandbox}")
    private String environment;

    @Value("${app.paypal.currency:USD}")
    private String currency;

    @Value("${app.paypal.mock-mode:false}")
    private boolean configuredMockMode;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public PayPalCheckoutService(DemoSupportService support, BookingWorkflowService bookingWorkflowService, ObjectMapper objectMapper) {
        this.support = support;
        this.bookingWorkflowService = bookingWorkflowService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        boolean mockMode = isMockMode();
        boolean checkoutEnabled = credentialsConfigured() && !mockMode;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", checkoutEnabled);
        response.put("clientId", checkoutEnabled ? clientId : "");
        response.put("currency", normalizedCurrency());
        response.put("environment", environment);
        response.put("amountUnit", "USD");
        response.put("note", checkoutEnabled
                ? "PayPal checkout is configured."
                : "Online payment is unavailable until PayPal credentials are configured.");
        return response;
    }

    // Backlog owner: AnNP - UC-40 Pay deposit/full amount via online payment sandbox.
    public Map<String, Object> createOrder(Long bookingId, ApiRequests.PayPalOrderCreate request) {
        Booking booking = onlineBookingOwnedByCustomer(bookingId, request.createdById());
        PaymentOption option = support.parseEnum(PaymentOption.class, request.paymentOption(), PaymentOption.deposit);
        BigDecimal bookingAmount = payableAmount(booking, option);
        BigDecimal settlementAmount = toSettlementAmount(bookingAmount);
        String idempotencyKey = "GZ-" + booking.getBookingCode() + "-" + option.name() + "-" + UUID.randomUUID();

        if (isMockMode()) {
            Payment payment = createPendingPayment(
                    booking,
                    option,
                    bookingAmount,
                    "MOCK-PAYPAL-ORDER-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(),
                    settlementAmount,
                    "CREATED",
                    idempotencyKey
            );
            return orderResponse(payment, settlementAmount, "CREATED");
        }
        requirePayPalCredentials();

        try {
            String orderId = createRemoteOrder(booking, option, settlementAmount, idempotencyKey);
            Payment payment = createPendingPayment(booking, option, bookingAmount, orderId, settlementAmount, "CREATED", idempotencyKey);
            return orderResponse(payment, settlementAmount, "CREATED");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not create PayPal order: " + exception.getMessage());
        }
    }

    // Backlog owner: AnNP - UC-40 Pay deposit/full amount via online payment sandbox.
    public Map<String, Object> captureOrder(Long bookingId, String orderId, ApiRequests.PayPalOrderCapture request) {
        Booking booking = onlineBookingOwnedByCustomer(bookingId, request.createdById());
        Payment payment = support.paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> support.notFound("PayPal order not found"));
        if (!payment.getBooking().getBookingId().equals(bookingId)) {
            throw support.badRequest("PayPal order does not belong to this booking");
        }
        if (payment.getStatus() == PaymentStatus.paid) {
            return bookingWorkflowService.bookingDetail(bookingId);
        }
        BigDecimal currentPayableAmount = payableAmount(booking, payment.getPaymentOption());
        if (payment.getAmount().compareTo(currentPayableAmount) != 0) {
            throw support.badRequest("Booking balance changed after the PayPal order was created");
        }

        String captureId;
        String providerStatus;
        if (isMockMode()) {
            captureId = "MOCK-PAYPAL-CAPTURE-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            providerStatus = "COMPLETED";
        } else {
            try {
                JsonNode capture = captureRemoteOrder(orderId);
                providerStatus = capture.path("status").asText();
                if (!"COMPLETED".equalsIgnoreCase(providerStatus)) {
                    payment.setProviderStatus(providerStatus);
                    payment.setGatewayMessage("PayPal capture was not completed: " + providerStatus);
                    payment.setStatus(PaymentStatus.failed);
                    throw support.badRequest("PayPal capture was not completed");
                }
                validateCaptureAmount(capture, payment);
                captureId = findCaptureId(capture);
            } catch (ApiException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not capture PayPal order: " + exception.getMessage());
            }
        }

        support.paymentRepository.findByProviderCaptureId(captureId)
                .filter(existing -> !existing.getPaymentId().equals(payment.getPaymentId()))
                .ifPresent(existing -> {
                    throw support.badRequest("PayPal capture has already been processed");
                });

        payment.setStatus(PaymentStatus.paid);
        payment.setProviderCaptureId(captureId);
        payment.setProviderStatus(providerStatus);
        payment.setTransactionCode(captureId);
        payment.setGatewayMessage(isMockMode() ? "Mock PayPal sandbox capture completed" : "PayPal capture completed");
        payment.setPaidAt(LocalDateTime.now());
        booking.setPaidAmount(support.money(booking.getPaidAmount().add(payment.getAmount())));
        booking.setRemainingAmount(support.money(booking.getTotalAmount().subtract(booking.getPaidAmount()).max(BigDecimal.ZERO)));
        if (booking.getStatus() == BookingStatus.pending && booking.getPaidAmount().compareTo(booking.getDepositAmount()) >= 0) {
            booking.setStatus(BookingStatus.confirmed);
            booking.setConfirmedAt(LocalDateTime.now());
        }
        support.notifyUser(booking.getCustomer(), booking, NotificationType.payment, "PayPal payment captured", "PayPal payment was captured for " + booking.getBookingCode() + ".");
        support.generateInvoice(booking);
        return bookingWorkflowService.bookingDetail(bookingId);
    }

    public Map<String, Object> cancelOrder(Long bookingId, String orderId) {
        Booking booking = onlineBookingOwnedByCustomer(bookingId, null);
        Payment payment = support.paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> support.notFound("PayPal order not found"));
        if (!payment.getBooking().getBookingId().equals(bookingId)) {
            throw support.badRequest("PayPal order does not belong to this booking");
        }
        if (payment.getStatus() == PaymentStatus.paid) {
            throw support.badRequest("A captured PayPal order cannot be cancelled");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.expired);
        payment.setExpiredAt(now);
        payment.setProviderStatus("CANCELLED_BY_CUSTOMER");
        payment.setGatewayMessage("PayPal checkout was cancelled by the customer");

        if (booking.getStatus() == BookingStatus.pending && booking.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            booking.setStatus(BookingStatus.expired);
            booking.setExpiredAt(now);
        }
        return bookingWorkflowService.bookingDetail(bookingId);
    }

    public RefundResult refundCapture(Payment payment, BigDecimal refundAmount, String requestId, String existingRefundId) {
        if (payment.getPaymentMethod() != PaymentMethod.paypal_sandbox
                || payment.getStatus() != PaymentStatus.paid
                || support.isBlank(payment.getProviderCaptureId())) {
            return new RefundResult(null, "FAILED", "The payment has no refundable PayPal capture.");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new RefundResult(null, "FAILED", "The PayPal refund amount must be positive.");
        }
        if (isMockMode()) {
            return new RefundResult(
                    "MOCK-PAYPAL-REFUND-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(),
                    "COMPLETED",
                    "Mock PayPal refund completed."
            );
        }
        if (!credentialsConfigured()) {
            return new RefundResult(null, "FAILED", "PayPal merchant credentials are not configured.");
        }

        try {
            JsonNode response;
            if (!support.isBlank(existingRefundId)) {
                response = sendJson(
                        "GET",
                        paypalBaseUrl() + "/v2/payments/refunds/"
                                + URLEncoder.encode(existingRefundId, StandardCharsets.UTF_8),
                        accessToken(), "", null);
            } else {
                Map<String, Object> payload = Map.of(
                        "amount", Map.of(
                                "value", support.money(refundAmount).toPlainString(),
                                "currency_code", normalizedCurrency()
                        ),
                        "note_to_payer", "GoalZone booking refund"
                );
                response = sendJson(
                        "POST",
                        paypalBaseUrl() + "/v2/payments/captures/"
                                + URLEncoder.encode(payment.getProviderCaptureId(), StandardCharsets.UTF_8) + "/refund",
                        accessToken(),
                        objectMapper.writeValueAsString(payload),
                        requestId
                );
            }
            String refundId = response.path("id").asText();
            String status = response.path("status").asText("PENDING").toUpperCase();
            if (support.isBlank(refundId)) {
                return new RefundResult(null, "FAILED", "PayPal did not return a refund transaction ID.");
            }
            return new RefundResult(refundId, status, "PayPal refund status: " + status + ".");
        } catch (Exception exception) {
            // A network timeout can happen after PayPal accepted the request. Keep the refund reserved and
            // retry with the same PayPal-Request-Id instead of allowing a second refund request.
            return new RefundResult(null, "PENDING", "PayPal has not confirmed the refund yet; retry safely with the same request ID.");
        }
    }

    private Payment createPendingPayment(
            Booking booking,
            PaymentOption option,
            BigDecimal amount,
            String orderId,
            BigDecimal settlementAmount,
            String providerStatus,
            String idempotencyKey
    ) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentCode("PAYPAL" + System.currentTimeMillis());
        payment.setPaymentOption(option);
        payment.setPaymentMethod(PaymentMethod.paypal_sandbox);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.pending);
        payment.setCreatedBy(booking.getCustomer());
        payment.setProviderOrderId(orderId);
        payment.setProviderStatus(providerStatus);
        payment.setCurrency(normalizedCurrency());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setGatewayMessage("PayPal order created for " + settlementAmount + " " + normalizedCurrency());
        return support.paymentRepository.save(payment);
    }

    private Map<String, Object> orderResponse(Payment payment, BigDecimal settlementAmount, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", payment.getProviderOrderId());
        response.put("status", status);
        response.put("paymentId", payment.getPaymentId());
        response.put("bookingId", payment.getBooking().getBookingId());
        response.put("bookingCode", payment.getBooking().getBookingCode());
        response.put("paymentOption", payment.getPaymentOption().name());
        response.put("amount", payment.getAmount());
        response.put("settlementAmount", settlementAmount);
        response.put("currency", normalizedCurrency());
        return response;
    }

    private BigDecimal payableAmount(Booking booking, PaymentOption option) {
        if (booking.getStatus() == BookingStatus.cancelled
                || booking.getStatus() == BookingStatus.rejected
                || booking.getStatus() == BookingStatus.expired
                || booking.getStatus() == BookingStatus.no_show) {
            throw support.badRequest("PayPal checkout is not available for a " + booking.getStatus().name() + " booking");
        }
        BigDecimal amount = switch (option) {
            case full -> booking.getTotalAmount().subtract(booking.getPaidAmount());
            case remaining -> booking.getRemainingAmount();
            case deposit -> booking.getDepositAmount().subtract(booking.getPaidAmount()).max(BigDecimal.ZERO);
        };
        amount = support.money(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("This booking has no payable amount for " + option.name());
        }
        return amount;
    }

    private Booking onlineBookingOwnedByCustomer(Long bookingId, Long requestedActorId) {
        Booking booking = support.getBooking(bookingId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        AppUser requester = principal.getAppUser();
        if (!"Customer".equalsIgnoreCase(requester.getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Online payment is available only to customers");
        }
        if (!booking.getCustomer().getUserId().equals(requester.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only pay for your own booking");
        }
        if (requestedActorId != null && !requestedActorId.equals(requester.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Online payment actor does not match the signed-in customer");
        }
        if (booking.getBookingSource() != BookingSource.online) {
            throw support.badRequest("PayPal checkout is available only for online bookings");
        }
        return booking;
    }

    private BigDecimal toSettlementAmount(BigDecimal bookingAmount) {
        // GoalZone stores and displays monetary values in USD. PayPal receives
        // the exact booking amount, so there is no hidden display conversion.
        return support.money(bookingAmount);
    }

    private String createRemoteOrder(Booking booking, PaymentOption option, BigDecimal settlementAmount, String idempotencyKey) throws Exception {
        String accessToken = accessToken();
        Map<String, Object> payload = Map.of(
                "intent", "CAPTURE",
                "purchase_units", java.util.List.of(Map.of(
                        "custom_id", booking.getBookingId() + ":" + option.name(),
                        "description", "GoalZone booking " + booking.getBookingCode(),
                        "amount", Map.of(
                                "currency_code", normalizedCurrency(),
                                "value", settlementAmount.toPlainString()
                        )
                )),
                "payment_source", Map.of("paypal", Map.of(
                        "experience_context", Map.of(
                                "payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED",
                                "brand_name", "GoalZone",
                                "landing_page", "LOGIN",
                                "shipping_preference", "NO_SHIPPING",
                                "user_action", "PAY_NOW",
                                "return_url", frontendBaseUrl + "/booking",
                                "cancel_url", frontendBaseUrl + "/booking"
                        )
                ))
        );
        JsonNode response = sendJson("POST", paypalBaseUrl() + "/v2/checkout/orders", accessToken, objectMapper.writeValueAsString(payload), idempotencyKey);
        String orderId = response.path("id").asText();
        if (support.isBlank(orderId)) {
            throw new IllegalStateException("PayPal did not return an order id");
        }
        return orderId;
    }

    private JsonNode captureRemoteOrder(String orderId) throws Exception {
        String accessToken = accessToken();
        return sendJson("POST", paypalBaseUrl() + "/v2/checkout/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8) + "/capture", accessToken, "{}", null);
    }

    private String accessToken() throws Exception {
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(paypalBaseUrl() + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("PayPal OAuth failed with status " + response.statusCode());
        }
        String token = objectMapper.readTree(response.body()).path("access_token").asText();
        if (support.isBlank(token)) {
            throw new IllegalStateException("PayPal OAuth did not return an access token");
        }
        return token;
    }

    private JsonNode sendJson(String method, String url, String accessToken, String body, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json");
        if (!support.isBlank(requestId)) {
            builder.header("PayPal-Request-Id", requestId);
        }
        HttpRequest request = "POST".equalsIgnoreCase(method)
                ? builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
                : builder.GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("PayPal API failed with status " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private void validateCaptureAmount(JsonNode capture, Payment payment) {
        JsonNode amount = capture.path("purchase_units").path(0).path("payments").path("captures").path(0).path("amount");
        String capturedCurrency = amount.path("currency_code").asText();
        BigDecimal capturedAmount = new BigDecimal(amount.path("value").asText("0"));
        BigDecimal expected = toSettlementAmount(payment.getAmount());
        if (!normalizedCurrency().equalsIgnoreCase(capturedCurrency) || capturedAmount.compareTo(expected) != 0) {
            throw support.badRequest("Captured PayPal amount does not match the expected booking charge");
        }
    }

    private String findCaptureId(JsonNode capture) {
        String captureId = capture.path("purchase_units").path(0).path("payments").path("captures").path(0).path("id").asText();
        if (support.isBlank(captureId)) {
            throw support.badRequest("PayPal did not return a capture id");
        }
        return captureId;
    }

    private boolean isMockMode() {
        return configuredMockMode;
    }

    private boolean credentialsConfigured() {
        return !support.isBlank(clientId) && !support.isBlank(clientSecret);
    }

    private void requirePayPalCredentials() {
        if (!credentialsConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PayPal checkout is not configured");
        }
    }

    private String normalizedCurrency() {
        return support.isBlank(currency) ? "USD" : currency.trim().toUpperCase();
    }

    private String paypalBaseUrl() {
        return "live".equalsIgnoreCase(environment) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }
}
