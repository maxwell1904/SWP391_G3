package com.swp391.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Payment;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.NotificationType;
import com.swp391.backend.enums.PaymentMethod;
import com.swp391.backend.enums.PaymentOption;
import com.swp391.backend.enums.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Value("${app.paypal.vnd-rate:25000}")
    private BigDecimal vndRate;

    @Value("${app.paypal.mock-mode:true}")
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
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", !support.isBlank(clientId) || mockMode);
        response.put("mockMode", mockMode);
        response.put("clientId", support.isBlank(clientId) ? "sb" : clientId);
        response.put("currency", normalizedCurrency());
        response.put("environment", environment);
        response.put("vndRate", vndRate);
        response.put("note", mockMode
                ? "PayPal credentials are not configured; using a local PayPal sandbox simulation."
                : "PayPal sandbox checkout is configured.");
        return response;
    }

    // Backlog owner: AnNP - UC-40 Pay deposit/full amount via online payment sandbox.
    public Map<String, Object> createOrder(Long bookingId, ApiRequests.PayPalOrderCreate request) {
        Booking booking = support.getBooking(bookingId);
        PaymentOption option = support.parseEnum(PaymentOption.class, request.paymentOption(), PaymentOption.deposit);
        BigDecimal vndAmount = payableAmount(booking, option);
        BigDecimal settlementAmount = toSettlementAmount(vndAmount);
        String idempotencyKey = "GZ-" + booking.getBookingCode() + "-" + option.name() + "-" + UUID.randomUUID();

        if (isMockMode()) {
            Payment payment = createPendingPayment(
                    booking,
                    request.createdById(),
                    option,
                    vndAmount,
                    "MOCK-PAYPAL-ORDER-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(),
                    settlementAmount,
                    "CREATED",
                    idempotencyKey
            );
            return orderResponse(payment, settlementAmount, "CREATED");
        }

        try {
            String orderId = createRemoteOrder(booking, option, settlementAmount, idempotencyKey);
            Payment payment = createPendingPayment(booking, request.createdById(), option, vndAmount, orderId, settlementAmount, "CREATED", idempotencyKey);
            return orderResponse(payment, settlementAmount, "CREATED");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not create PayPal order: " + exception.getMessage());
        }
    }

    // Backlog owner: AnNP - UC-40 Pay deposit/full amount via online payment sandbox.
    public Map<String, Object> captureOrder(Long bookingId, String orderId, ApiRequests.PayPalOrderCapture request) {
        Booking booking = support.getBooking(bookingId);
        Payment payment = support.paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> support.notFound("PayPal order not found"));
        if (!payment.getBooking().getBookingId().equals(bookingId)) {
            throw support.badRequest("PayPal order does not belong to this booking");
        }
        if (payment.getStatus() == PaymentStatus.paid) {
            return bookingWorkflowService.bookingDetail(bookingId);
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
        if (request.createdById() != null) {
            payment.setCreatedBy(support.getUser(request.createdById()));
        }

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

    private Payment createPendingPayment(
            Booking booking,
            Long createdById,
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
        payment.setCreatedBy(createdById == null ? booking.getCustomer() : support.getUser(createdById));
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
        response.put("mockMode", isMockMode());
        return response;
    }

    private BigDecimal payableAmount(Booking booking, PaymentOption option) {
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

    private BigDecimal toSettlementAmount(BigDecimal vndAmount) {
        if ("VND".equalsIgnoreCase(normalizedCurrency())) {
            return support.money(vndAmount);
        }
        if (vndRate == null || vndRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("PayPal VND conversion rate is invalid");
        }
        return vndAmount.divide(vndRate, 2, RoundingMode.HALF_UP);
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
        return configuredMockMode || support.isBlank(clientId) || support.isBlank(clientSecret);
    }

    private String normalizedCurrency() {
        return support.isBlank(currency) ? "USD" : currency.trim().toUpperCase();
    }

    private String paypalBaseUrl() {
        return "live".equalsIgnoreCase(environment) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }
}
