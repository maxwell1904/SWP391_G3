package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.BookingWorkflowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingWorkflowService bookingWorkflowService;

    public BookingController(BookingWorkflowService bookingWorkflowService) {
        this.bookingWorkflowService = bookingWorkflowService;
    }

    @PostMapping("/checkout-preview")
    public Object checkoutPreview(@RequestBody ApiRequests.PromotionApply request) {
        return bookingWorkflowService.previewCheckout(request);
    }

    @PostMapping
    public Object createBooking(@RequestBody ApiRequests.BookingCreate request) {
        return bookingWorkflowService.createBooking(request);
    }

    @GetMapping
    public Object bookings(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return bookingWorkflowService.bookings(customerId, date);
    }

    @GetMapping("/{bookingId}")
    public Object bookingDetail(@PathVariable Long bookingId) {
        return bookingWorkflowService.bookingDetail(bookingId);
    }

    @GetMapping("/{bookingId}/cancellation-preview")
    public Object cancellationPreview(@PathVariable Long bookingId) {
        return bookingWorkflowService.previewCancellation(bookingId);
    }

    @PutMapping("/{bookingId}/reschedule")
    public Object reschedule(@PathVariable Long bookingId, @RequestBody ApiRequests.BookingReschedule request) {
        return bookingWorkflowService.reschedule(bookingId, request);
    }

    @PutMapping("/{bookingId}/status")
    public Object updateStatus(@PathVariable Long bookingId, @RequestBody ApiRequests.BookingStatusUpdate request) {
        return bookingWorkflowService.updateBookingStatus(bookingId, request);
    }

    @PutMapping("/{bookingId}/services")
    public Object updateServices(@PathVariable Long bookingId, @RequestBody ApiRequests.BookingServicesUpdate request) {
        return bookingWorkflowService.updateBookingServices(bookingId, request);
    }
}
