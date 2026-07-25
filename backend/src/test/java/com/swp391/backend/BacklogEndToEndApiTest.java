package com.swp391.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp391.backend.entity.AppUser;
import com.swp391.backend.repository.AppUserRepository;
import com.swp391.backend.service.BookingMaintenanceScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Black-box regression coverage for the assessed MVP workflows.  The calls use the
 * same JWT filter, controllers, services and JPA persistence path as the web client.
 * PayPal is switched to its local sandbox adapter so no real external charge is made.
 */
@SpringBootTest(properties = "app.paypal.mock-mode=true")
@AutoConfigureMockMvc
@Transactional
class BacklogEndToEndApiTest {
    private static final String PASSWORD = "GoalZone@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BookingMaintenanceScheduler bookingMaintenanceScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void customerCanPriceBookPayRescheduleCancelRefundAndSeeOwnHistory() throws Exception {
        JsonNode fields = exchange(get("/api/fields"), 200);
        assertThat(fields.isArray()).isTrue();
        assertThat(fields.size()).isGreaterThan(0);
        JsonNode services = exchange(get("/api/services"), 200);
        assertThat(services.isArray()).isTrue();
        JsonNode levels = exchange(get("/api/membership/levels"), 200);
        assertThat(levels.isArray()).isTrue();

        JsonNode customerLogin = login("customer@goalzone.local");
        String customerToken = token(customerLogin);
        long customerId = userId(customerLogin);
        long otherCustomerId = userId(login("member@goalzone.local"));
        JsonNode membership = exchange(auth(get("/api/membership/" + customerId + "/progress"), customerToken), 200);
        assertThat(membership.path("customer").path("userId").asLong()).isEqualTo(customerId);

        List<JsonNode> slots = availableSlots(LocalDate.now().plusDays(1));
        JsonNode lowerPricedSlot = slots.stream()
                .min(Comparator.comparing(this::slotPrice))
                .orElseThrow();
        JsonNode higherPricedSlot = slots.stream()
                .max(Comparator.comparing(this::slotPrice))
                .orElseThrow();
        assertThat(slotPrice(higherPricedSlot)).isGreaterThan(slotPrice(lowerPricedSlot));

        JsonNode preview = exchange(post("/api/bookings/checkout-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", customerId,
                        "slotId", higherPricedSlot.path("slotId").asLong(),
                        "promotionCode", "WELCOME10",
                        "bookingSource", "online",
                        "services", List.of()
                ))), 200);
        assertThat(preview.path("totalAmount").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(preview.path("promotionDiscountAmount").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(preview.has("customer")).isFalse();
        assertThat(preview.path("customerId").asLong()).isEqualTo(customerId);

        JsonNode booked = createOnlineBooking(customerToken, customerId, higherPricedSlot.path("slotId").asLong(), "E2E payment and reschedule");
        long bookingId = booked.path("bookingId").asLong();
        JsonNode customerEditedServices = exchange(auth(put("/api/bookings/" + bookingId + "/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("services", List.of(Map.of(
                        "serviceId", services.get(0).path("extraServiceId").asLong(), "quantity", 1))))), customerToken), 200);
        assertThat(customerEditedServices.path("services").size()).isEqualTo(1);
        JsonNode customerIssue = exchange(auth(post("/api/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reporterId", customerId, "bookingId", bookingId,
                        "title", "E2E customer issue", "description", "Reported through customer support flow"))), customerToken), 200);
        assertThat(customerIssue.path("reporter").asText()).isEqualTo("Nguyen Van Customer");
        JsonNode order = createPayPalOrder(customerToken, bookingId, customerId, "full");
        JsonNode paid = capturePayPalOrder(customerToken, bookingId, order.path("orderId").asText(), customerId);
        assertThat(paid.path("paymentStatus").asText()).isEqualTo("paid");
        assertThat(paid.path("remainingAmount").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);

        JsonNode paymentHistory = exchange(auth(get("/api/payments"), customerToken), 200);
        assertThat(containsBookingCode(paymentHistory, booked.path("bookingCode").asText())).isTrue();

        JsonNode rescheduled = exchange(auth(put("/api/bookings/" + bookingId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newSlotId", lowerPricedSlot.path("slotId").asLong(), "note", "E2E lower-price reschedule"))), customerToken), 200);
        assertThat(rescheduled.path("slotId").asLong()).isEqualTo(lowerPricedSlot.path("slotId").asLong());
        assertThat(rescheduled.path("paidAmount").decimalValue()).isEqualByComparingTo(paid.path("paidAmount").decimalValue());
        assertThat(rescheduled.path("refundableAmount").decimalValue()).isGreaterThan(BigDecimal.ZERO);

        JsonNode refund = exchange(auth(post("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", bookingId,
                        "refundReason", "Price difference after reschedule",
                        "approveNow", false
                ))), customerToken), 200);
        assertThat(refund.path("status").asText()).isEqualTo("requested");

        JsonNode staffLogin = login("staff@goalzone.local");
        String staffToken = token(staffLogin);
        long staffId = userId(staffLogin);
        assertThat(exchange(auth(post("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", bookingId,
                        "refundReason", "Staff must review rather than request"
                ))), staffToken), 403).path("error").asText())
                .contains("submitted by the customer");
        JsonNode approvedRefund = exchange(auth(put("/api/refunds/" + refund.path("refundId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "approved", "processedById", staffId, "note", "Validated by E2E"))), staffToken), 200);
        assertThat(approvedRefund.path("status").asText()).isEqualTo("approved");
        JsonNode rejectedFakeCompletion = exchange(auth(put("/api/refunds/" + refund.path("refundId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "completed", "processedById", staffId, "note", "Must not bypass PayPal"))), staffToken), 400);
        assertThat(rejectedFakeCompletion.path("error").asText()).contains("Invalid refund transition");
        JsonNode completedRefund = exchange(auth(put("/api/refunds/" + refund.path("refundId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "processing", "processedById", staffId, "note", "Sent to PayPal by E2E"))), staffToken), 200);
        assertThat(completedRefund.path("status").asText()).isEqualTo("completed");
        assertThat(completedRefund.path("paymentMethod").asText()).isEqualTo("paypal_sandbox");
        assertThat(completedRefund.path("providerStatus").asText()).isEqualTo("COMPLETED");
        assertThat(completedRefund.path("transactionCode").asText()).startsWith("MOCK-PAYPAL-REFUND-");

        JsonNode cancellableSlot = availableSlots(LocalDate.now().plusDays(3)).get(0);
        JsonNode cancellableBooking = createOnlineBooking(customerToken, customerId, cancellableSlot.path("slotId").asLong(), "E2E cancellation");
        long cancellableBookingId = cancellableBooking.path("bookingId").asLong();
        JsonNode depositOrder = createPayPalOrder(customerToken, cancellableBookingId, customerId, "deposit");
        capturePayPalOrder(customerToken, cancellableBookingId, depositOrder.path("orderId").asText(), customerId);
        JsonNode cancellationPreview = exchange(auth(get("/api/bookings/" + cancellableBookingId + "/cancellation-preview"), customerToken), 200);
        assertThat(cancellationPreview.path("paidAmount").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        JsonNode cancelled = exchange(auth(put("/api/bookings/" + cancellableBookingId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "cancelled", "note", "Customer E2E cancellation"))), customerToken), 200);
        assertThat(cancelled.path("status").asText()).isEqualTo("cancelled");
        assertThat(cancelled.path("refundableAmount").decimalValue())
                .isEqualByComparingTo(cancellationPreview.path("refundableAmount").decimalValue());
        assertThat(exchange(auth(post("/api/payments/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", cancellableBookingId,
                        "createdById", staffId,
                        "paymentOption", "remaining",
                        "paymentMethod", "cash",
                        "success", true
                ))), staffToken), 400).path("error").asText())
                .contains("cannot be captured");
        assertThat(exchange(auth(put("/api/bookings/" + cancellableBookingId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "confirmed", "note", "Invalid transition"))), staffToken), 400)
                .path("error").asText()).contains("cannot change");

        JsonNode notifications = exchange(auth(get("/api/notifications/" + customerId), customerToken), 200);
        assertThat(notifications.isArray()).isTrue();
        assertThat(exchange(auth(get("/api/membership/" + otherCustomerId + "/progress"), customerToken), 403).path("error").asText()).isNotBlank();
        exchange(auth(get("/api/account/customers"), customerToken), 403);
    }

    @Test
    void staffCanRunOperationsLifecycleIssueAndCustomerActivityFlows() throws Exception {
        JsonNode staffLogin = login("staff@goalzone.local");
        String staffToken = token(staffLogin);
        long staffId = userId(staffLogin);
        JsonNode customerLogin = login("customer@goalzone.local");
        String customerToken = token(customerLogin);
        long customerId = userId(customerLogin);
        JsonNode walkInCustomers = exchange(auth(get("/api/account/customers"), staffToken), 200);
        assertThat(walkInCustomers).anySatisfy(customer ->
                assertThat(customer.path("userId").asLong()).isEqualTo(customerId));

        JsonNode operatorSlot = availableSlots(LocalDate.now().plusDays(1)).get(0);
        JsonNode customerBooking = createOnlineBooking(customerToken, customerId, operatorSlot.path("slotId").asLong(), "E2E counter settlement");
        assertThat(exchange(auth(post("/api/bookings/" + customerBooking.path("bookingId").asLong() + "/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("createdById", staffId, "paymentOption", "full"))), staffToken), 403)
                .path("error").asText()).isNotBlank();
        assertThat(exchange(auth(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", customerId,
                        "staffId", staffId,
                        "slotId", availableSlots(LocalDate.now().plusDays(2)).get(0).path("slotId").asLong(),
                        "bookingSource", "online",
                        "services", List.of()
                ))), staffToken), 403).path("error").asText()).isNotBlank();

        JsonNode bookings = exchange(auth(get("/api/bookings"), staffToken), 200);
        assertThat(bookings.isArray()).isTrue();
        assertThat(bookings.size()).isGreaterThan(0);
        assertThat(exchange(auth(post("/api/payments/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", customerBooking.path("bookingId").asLong(),
                        "createdById", staffId,
                        "paymentOption", "full",
                        "paymentMethod", "online_sandbox",
                        "success", true
                ))), staffToken), 400).path("error").asText()).isNotBlank();
        JsonNode remainingPayment = exchange(auth(post("/api/payments/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", customerBooking.path("bookingId").asLong(),
                        "createdById", staffId,
                        "paymentOption", "remaining",
                        "paymentMethod", "cash",
                        "success", true
                ))), staffToken), 200);
        assertThat(remainingPayment.path("paymentStatus").asText()).isEqualTo("paid");
        exchange(auth(get("/api/account/users/" + customerId + "/activity"), staffToken), 403);
        JsonNode calendar = exchange(auth(get("/api/operations/calendar").param("date", LocalDate.now().plusDays(1).toString()), staffToken), 200);
        assertThat(calendar.isArray()).isTrue();

        JsonNode field = exchange(get("/api/fields"), 200).get(0);
        JsonNode blocked = exchange(auth(post("/api/slots/block")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fieldId", field.path("fieldId").asLong(),
                        "slotDate", LocalDate.now().plusDays(4).toString(),
                        "startTime", "12:00",
                        "endTime", "14:00",
                        "blockReason", "E2E maintenance",
                        "blockNote", "Operations test",
                        "createdById", staffId
                ))), staffToken), 200);
        assertThat(blocked.path("status").asText()).isEqualTo("blocked");
        JsonNode unblocked = exchange(auth(put("/api/slots/" + blocked.path("slotId").asLong() + "/unblock"), staffToken), 200);
        assertThat(unblocked.path("status").asText()).isEqualTo("available");

        JsonNode issue = exchange(auth(post("/api/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "reporterId", staffId,
                        "fieldId", field.path("fieldId").asLong(),
                        "assignedStaffId", staffId,
                        "title", "E2E equipment check",
                        "description", "Verify staff issue lifecycle"
                ))), staffToken), 200);
        JsonNode resolvedIssue = exchange(auth(put("/api/issues/" + issue.path("issueId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "resolved", "assignedStaffId", staffId, "resolutionNote", "Checked and resolved"))), staffToken), 200);
        assertThat(resolvedIssue.path("status").asText()).isEqualTo("resolved");

        JsonNode memberLogin = login("member@goalzone.local");
        long memberId = userId(memberLogin);
        JsonNode slot = availableSlots(LocalDate.now().plusDays(2)).get(0);
        JsonNode walkIn = exchange(auth(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", memberId,
                        "staffId", staffId,
                        "slotId", slot.path("slotId").asLong(),
                        "bookingSource", "walk_in",
                        "services", List.of(),
                        "note", "E2E walk-in"
                ))), staffToken), 200);
        long walkInId = walkIn.path("bookingId").asLong();

        JsonNode service = exchange(get("/api/services"), 200).get(0);
        JsonNode servicesUpdated = exchange(auth(put("/api/bookings/" + walkInId + "/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("services", List.of(Map.of("serviceId", service.path("extraServiceId").asLong(), "quantity", 1))))), staffToken), 200);
        assertThat(servicesUpdated.path("services").size()).isEqualTo(1);
        assertThat(servicesUpdated.path("invoice").isObject()).isTrue();
        assertThat(servicesUpdated.path("invoice").path("serviceAmount").decimalValue())
                .isEqualByComparingTo(servicesUpdated.path("serviceTotalAmount").decimalValue());
        assertThat(servicesUpdated.path("invoice").path("totalAmount").decimalValue())
                .isEqualByComparingTo(servicesUpdated.path("totalAmount").decimalValue());
        JsonNode paid = exchange(auth(post("/api/payments/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", walkInId,
                        "createdById", staffId,
                        "paymentOption", "full",
                        "paymentMethod", "cash",
                        "success", true
                ))), staffToken), 200);
        assertThat(paid.path("paymentStatus").asText()).isEqualTo("paid");
        JsonNode checkedIn = exchange(auth(put("/api/bookings/" + walkInId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "checked_in", "staffId", staffId, "note", "Arrived"))), staffToken), 200);
        assertThat(checkedIn.path("status").asText()).isEqualTo("checked_in");
        JsonNode completed = exchange(auth(put("/api/bookings/" + walkInId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "completed", "staffId", staffId, "note", "Finished"))), staffToken), 200);
        assertThat(completed.path("status").asText()).isEqualTo("completed");

        JsonNode rejectedSlot = availableSlots(LocalDate.now().plusDays(3)).get(0);
        JsonNode pendingOnline = createOnlineBooking(customerToken, customerId, rejectedSlot.path("slotId").asLong(), "E2E rejection");
        JsonNode rejected = exchange(auth(put("/api/bookings/" + pendingOnline.path("bookingId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "rejected", "staffId", staffId, "note", "Invalid request"))), staffToken), 200);
        assertThat(rejected.path("status").asText()).isEqualTo("rejected");

        JsonNode noShowSlot = availableSlots(LocalDate.now().plusDays(4)).get(0);
        JsonNode noShowWalkIn = exchange(auth(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", memberId,
                        "staffId", staffId,
                        "slotId", noShowSlot.path("slotId").asLong(),
                        "bookingSource", "walk_in",
                        "services", List.of(),
                        "note", "E2E no-show"
                ))), staffToken), 200);
        exchange(auth(post("/api/payments/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "bookingId", noShowWalkIn.path("bookingId").asLong(),
                        "createdById", staffId,
                        "paymentOption", "full",
                        "paymentMethod", "cash",
                        "success", true
                ))), staffToken), 200);
        JsonNode noShow = exchange(auth(put("/api/bookings/" + noShowWalkIn.path("bookingId").asLong() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "no_show", "staffId", staffId, "note", "Customer did not arrive"))), staffToken), 200);
        assertThat(noShow.path("status").asText()).isEqualTo("no_show");
        assertThat(exchange(auth(get("/api/payments"), staffToken), 200).isArray()).isTrue();
        assertThat(exchange(auth(get("/api/refunds"), staffToken), 200).isArray()).isTrue();
    }

    @Test
    void schedulerExpiresUnpaidHoldsAndSendsOneUpcomingBookingReminder() throws Exception {
        JsonNode customerLogin = login("customer@goalzone.local");
        String customerToken = token(customerLogin);
        long customerId = userId(customerLogin);
        List<JsonNode> slots = availableSlots(LocalDate.now().plusDays(1));

        JsonNode staleSlot = slots.stream()
                .filter(slot -> slot.path("startTime").asText().startsWith("06:00"))
                .findFirst()
                .orElseThrow();
        JsonNode staleBooking = createOnlineBooking(customerToken, customerId, staleSlot.path("slotId").asLong(), "E2E stale payment hold");
        jdbcTemplate.update(
                "update booking set created_at = ? where booking_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)),
                staleBooking.path("bookingId").asLong()
        );
        entityManager.clear();
        assertThat(bookingMaintenanceScheduler.expireUnpaidPendingBookings()).isPositive();
        assertThat(exchange(auth(get("/api/bookings/" + staleBooking.path("bookingId").asLong()), customerToken), 200)
                .path("status").asText()).isEqualTo("expired");

        JsonNode reminderSlot = slots.stream()
                .filter(slot -> slot.path("slotId").asLong() != staleSlot.path("slotId").asLong())
                .filter(slot -> slot.path("startTime").asText().equals(staleSlot.path("startTime").asText()))
                .findFirst()
                .orElseThrow();
        JsonNode staffLogin = login("staff@goalzone.local");
        String staffToken = token(staffLogin);
        long staffId = userId(staffLogin);
        JsonNode reminderBooking = exchange(auth(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", customerId,
                        "staffId", staffId,
                        "slotId", reminderSlot.path("slotId").asLong(),
                        "bookingSource", "walk_in",
                        "services", List.of(),
                        "note", "E2E booking reminder"
                ))), staffToken), 200);
        jdbcTemplate.update("update system_setting set setting_value = '48' where setting_key = 'notification.booking_reminder_hours'");
        entityManager.clear();
        assertThat(bookingMaintenanceScheduler.sendUpcomingBookingReminders()).isPositive();
        JsonNode notifications = exchange(auth(get("/api/notifications/" + customerId), customerToken), 200);
        assertThat(notifications).anySatisfy(notification ->
                assertThat(notification.path("type").asText()).isEqualTo("booking_reminder"));
    }

    @Test
    void availabilityAssistantReturnsRankedAvailableSlots() throws Exception {
        JsonNode suggestions = exchange(get("/api/slots/suggestions")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("preferredTime", "08:00"), 200);
        assertThat(suggestions.isArray()).isTrue();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).path("slotId").asLong()).isPositive();
        assertThat(suggestions.get(0).path("reasons")).isNotEmpty();
        assertThat(suggestions.get(0).path("score").asInt()).isPositive();

        exchange(get("/api/slots/suggestions")
                .param("date", LocalDate.now().minusDays(1).toString()), 400);
        exchange(get("/api/slots/suggestions")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("maxPrice", "-1"), 400);

        JsonNode customerLogin = login("customer@goalzone.local");
        exchange(get("/api/slots/suggestions")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("customerId", customerLogin.path("user").path("userId").asText()), 403);
        JsonNode customerSuggestions = exchange(auth(get("/api/slots/suggestions")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("customerId", customerLogin.path("user").path("userId").asText()), token(customerLogin)), 200);
        assertThat(customerSuggestions).isNotEmpty();
    }

    @Test
    void administratorCanManageFieldsPricesServicesPeoplePoliciesMembershipPromotionsAndReports() throws Exception {
        JsonNode adminLogin = login("admin@goalzone.local");
        String adminToken = token(adminLogin);
        long adminId = userId(adminLogin);
        JsonNode users = exchange(auth(get("/api/account/users"), adminToken), 200);
        assertThat(users.size()).isGreaterThanOrEqualTo(4);
        assertThat(exchange(auth(get("/api/account/staff"), adminToken), 200).isArray()).isTrue();

        JsonNode type = exchange(get("/api/field-types"), 200).get(0);
        String suffix = String.valueOf(System.nanoTime());
        JsonNode managedField = exchange(auth(post("/api/admin/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fieldTypeId", type.path("fieldTypeId").asLong(),
                        "fieldName", "E2E Field " + suffix,
                        "description", "Created by admin E2E",
                        "location", "E2E zone",
                        "surfaceType", "Artificial grass",
                        "status", "active"
                ))), adminToken), 200);
        long fieldId = managedField.path("fieldId").asLong();
        MockMultipartFile image = new MockMultipartFile("file", "pitch.png", "image/png", new byte[]{1, 2, 3, 4});
        JsonNode uploaded = exchange(auth(multipart("/api/admin/uploads/images").file(image), adminToken), 200);
        assertThat(uploaded.path("url").asText()).startsWith("/api/uploads/images/");
        assertThat(mockMvc.perform(get(uploaded.path("url").asText())).andReturn().getResponse().getStatus()).isEqualTo(200);
        exchange(multipart("/api/admin/uploads/images").file(image), 403);
        JsonNode fieldPrice = exchange(auth(post("/api/admin/fields/" + fieldId + "/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "dayType", "all",
                        "startTime", "12:00",
                        "endTime", "14:00",
                        "price", 20.00,
                        "effectiveFrom", LocalDate.now().toString(),
                        "status", "active"
                ))), adminToken), 200);
        JsonNode updatedPrice = exchange(auth(put("/api/admin/field-prices/" + fieldPrice.path("fieldPriceId").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "dayType", "all",
                        "startTime", "12:00",
                        "endTime", "14:00",
                        "price", 21.00,
                        "effectiveFrom", LocalDate.now().toString(),
                        "status", "active"
                ))), adminToken), 200);
        assertThat(updatedPrice.path("price").decimalValue()).isEqualByComparingTo("21.00");

        JsonNode service = exchange(auth(post("/api/admin/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "serviceName", "E2E Cones " + suffix,
                        "serviceType", "rental",
                        "description", "Training cones",
                        "unitName", "set",
                        "unitPrice", 4.50,
                        "stockQuantity", 8,
                        "maxQuantityPerBooking", 2,
                        "status", "active"
                ))), adminToken), 200);
        JsonNode updatedService = exchange(auth(put("/api/admin/services/" + service.path("extraServiceId").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "serviceName", "E2E Cones " + suffix,
                        "serviceType", "rental",
                        "description", "Updated training cones",
                        "unitName", "set",
                        "unitPrice", 5.00,
                        "stockQuantity", 9,
                        "maxQuantityPerBooking", 2,
                        "status", "active"
                ))), adminToken), 200);
        assertThat(updatedService.path("unitPrice").decimalValue()).isEqualByComparingTo("5.00");

        JsonNode promotion = exchange(auth(post("/api/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "promotionCode", "E2E" + suffix.substring(Math.max(0, suffix.length() - 8)),
                        "promotionName", "E2E promotion",
                        "description", "Admin promotion test",
                        "discountType", "percent",
                        "discountValue", 5,
                        "minBookingAmount", 5,
                        "usageLimit", 10,
                        "startDate", LocalDate.now().toString(),
                        "endDate", LocalDate.now().plusDays(7).toString(),
                        "status", "active"
                ))), adminToken), 200);
        assertThat(promotion.path("promotionId").asLong()).isPositive();

        JsonNode membershipLevel = exchange(auth(post("/api/membership/levels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "levelName", "E2E " + suffix,
                        "requiredCompletedBookings", 12,
                        "discountPercent", 12,
                        "benefitDescription", "E2E membership",
                        "displayOrder", 99,
                        "status", "active"
                ))), adminToken), 200);
        JsonNode updatedMembershipLevel = exchange(auth(put("/api/membership/levels/" + membershipLevel.path("membershipLevelId").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "levelName", "E2E " + suffix,
                        "requiredCompletedBookings", 13,
                        "discountPercent", 13,
                        "benefitDescription", "Updated E2E membership",
                        "displayOrder", 99,
                        "status", "active"
                ))), adminToken), 200);
        assertThat(updatedMembershipLevel.path("discountPercent").decimalValue()).isEqualByComparingTo("13");

        JsonNode setting = exchange(auth(put("/api/settings/deposit.default_percent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("settingValue", "25", "updatedById", adminId))), adminToken), 200);
        assertThat(setting.path("settingValue").asText()).isEqualTo("25");
        JsonNode refundPolicy = exchange(auth(put("/api/settings/refund.before_24h_percent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("settingValue", "90", "updatedById", adminId))), adminToken), 200);
        assertThat(refundPolicy.path("settingValue").asText()).isEqualTo("90");
        JsonNode customerLogin = login("customer@goalzone.local");
        String customerToken = token(customerLogin);
        long customerId = userId(customerLogin);
        JsonNode customerActivity = exchange(
                auth(get("/api/account/users/" + customerId + "/activity"), adminToken), 200);
        assertThat(customerActivity.path("user").path("userId").asLong()).isEqualTo(customerId);
        exchange(auth(put("/api/account/users/" + customerId + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Admin must not edit this customer",
                        "phone", customerActivity.path("user").path("phone").asText(),
                        "address", "Forbidden admin edit"
                ))), adminToken), 403);
        JsonNode reportBooking = createOnlineBooking(customerToken, customerId,
                availableSlots(LocalDate.now().plusDays(1)).get(0).path("slotId").asLong(), "E2E report booking");
        JsonNode reportOrder = createPayPalOrder(customerToken, reportBooking.path("bookingId").asLong(), customerId, "full");
        capturePayPalOrder(customerToken, reportBooking.path("bookingId").asLong(), reportOrder.path("orderId").asText(), customerId);
        assertThat(exchange(auth(get("/api/reports"), adminToken), 200).path("bookingCount").asLong()).isPositive();

        JsonNode newStaff = exchange(auth(post("/api/account/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "E2E Staff",
                        "email", "staff." + suffix + "@goalzone.local",
                        "phone", uniquePhone(),
                        "password", "E2EStaff@123",
                        "status", "active"
                ))), adminToken), 200);
        JsonNode staffUpdated = exchange(auth(put("/api/account/staff/" + newStaff.path("userId").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "E2E Staff Updated",
                        "email", newStaff.path("email").asText(),
                        "phone", newStaff.path("phone").asText(),
                        "password", "",
                        "status", "active"
                ))), adminToken), 200);
        assertThat(staffUpdated.path("fullName").asText()).isEqualTo("E2E Staff Updated");

        JsonNode member = login("member@goalzone.local");
        long memberId = userId(member);
        JsonNode locked = exchange(auth(put("/api/account/users/" + memberId + "/lock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountLocked", true, "lockReason", "E2E access review"))), adminToken), 200);
        assertThat(locked.path("accountLocked").asBoolean()).isTrue();
        assertThat(locked.path("lockReason").asText()).isEqualTo("E2E access review");
        assertThat(locked.path("status").asText()).isEqualTo("locked");
        JsonNode blockedLogin = exchange(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("emailOrPhone", "member@goalzone.local", "password", PASSWORD))), 403);
        assertThat(blockedLogin.path("error").asText()).contains("check your email");
        JsonNode unlocked = exchange(auth(put("/api/account/users/" + memberId + "/lock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountLocked", false, "lockReason", ""))), adminToken), 200);
        assertThat(unlocked.path("accountLocked").asBoolean()).isFalse();
        assertThat(unlocked.path("status").asText()).isEqualTo("active");
        assertThat(login("member@goalzone.local").path("user").path("accountLocked").asBoolean()).isFalse();
    }

    @Test
    void accountRegistrationVerificationRecoveryProfilePasswordAndAuthorizationRulesWork() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "e2e." + suffix + "@goalzone.local";
        String phone = uniquePhone();
        JsonNode registered = exchange(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "E2E Customer",
                        "email", email,
                        "phone", phone,
                        "password", "E2ECustomer@123",
                        "confirmPassword", "E2ECustomer@123"
                ))), 200);
        long userId = registered.path("user").path("userId").asLong();
        assertThat(registered.path("verificationRequired").asBoolean()).isTrue();
        assertThat(exchange(post("/api/account/email/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", userId))), 200).path("user").path("userId").asLong()).isEqualTo(userId);

        AppUser persisted = userRepository.findById(userId).orElseThrow();
        exchange(post("/api/account/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", userId, "token", persisted.getEmailVerificationToken()))), 200);
        JsonNode login = login(email, "E2ECustomer@123");
        String token = token(login);
        JsonNode profile = exchange(auth(put("/api/account/users/" + userId + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "E2E Customer Updated", "phone", phone, "address", "E2E address"))), token), 200);
        assertThat(profile.path("fullName").asText()).isEqualTo("E2E Customer Updated");

        exchange(post("/api/account/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))), 200);
        persisted = userRepository.findById(userId).orElseThrow();
        String resetToken = persisted.getPasswordResetToken();
        assertThat(exchange(post("/api/account/validate-reset-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", userId, "token", resetToken))), 200).path("valid").asBoolean()).isTrue();
        exchange(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", userId, "token", resetToken, "newPassword", "E2EReset@123", "confirmPassword", "E2EReset@123"))), 200);
        JsonNode resetLogin = login(email, "E2EReset@123");
        String resetLoginToken = token(resetLogin);
        exchange(auth(put("/api/account/users/" + userId + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", "E2EReset@123", "newPassword", "E2EChanged@123", "confirmPassword", "E2EChanged@123"))), resetLoginToken), 200);
        JsonNode changedLogin = login(email, "E2EChanged@123");
        String changedToken = token(changedLogin);

        assertThat(exchange(auth(post("/api/account/logout"), changedToken), 200).path("message").asText()).contains("Signed out");
        exchange(auth(get("/api/bookings"), changedToken), 403);

        JsonNode customerLogin = login("customer@goalzone.local");
        String customerToken = token(customerLogin);
        exchange(get("/api/settings"), 403);
        exchange(auth(post("/api/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), customerToken), 403);
        exchange(auth(post("/api/membership/levels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("levelName", "Forbidden", "requiredCompletedBookings", 1, "discountPercent", 1, "displayOrder", 77, "status", "active"))), customerToken), 403);
    }

    private JsonNode createOnlineBooking(String token, long customerId, long slotId, String note) throws Exception {
        return exchange(auth(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "customerId", customerId,
                        "slotId", slotId,
                        "bookingSource", "online",
                        "services", List.of(),
                        "note", note
                ))), token), 200);
    }

    private JsonNode createPayPalOrder(String token, long bookingId, long userId, String paymentOption) throws Exception {
        return exchange(auth(post("/api/bookings/" + bookingId + "/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("createdById", userId, "paymentOption", paymentOption))), token), 200);
    }

    private JsonNode capturePayPalOrder(String token, long bookingId, String orderId, long userId) throws Exception {
        return exchange(auth(post("/api/bookings/" + bookingId + "/paypal/orders/" + orderId + "/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("createdById", userId))), token), 200);
    }

    private JsonNode login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    private JsonNode login(String email, String password) throws Exception {
        return exchange(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("emailOrPhone", email, "password", password))), 200);
    }

    private List<JsonNode> availableSlots(LocalDate date) throws Exception {
        JsonNode response = exchange(get("/api/slots/search").param("date", date.toString()), 200);
        List<JsonNode> slots = new ArrayList<>();
        response.forEach(slot -> {
            if (slot.path("available").asBoolean()) {
                slots.add(slot);
            }
        });
        assertThat(slots).isNotEmpty();
        return slots;
    }

    private BigDecimal slotPrice(JsonNode slot) {
        return slot.path("price").decimalValue();
    }

    private boolean containsBookingCode(JsonNode payments, String bookingCode) {
        for (JsonNode payment : payments) {
            if (bookingCode.equals(payment.path("bookingCode").asText())) {
                return true;
            }
        }
        return false;
    }

    private String uniquePhone() {
        long number = Math.floorMod(System.nanoTime(), 10_000_000L);
        return String.format("079%07d", number);
    }

    private String token(JsonNode login) {
        String token = login.path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private long userId(JsonNode login) {
        long userId = login.path("user").path("userId").asLong();
        assertThat(userId).isPositive();
        return userId;
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode exchange(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String body = result.getResponse().getContentAsString();
        return body == null || body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
