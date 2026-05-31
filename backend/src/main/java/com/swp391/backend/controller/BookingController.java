package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.MvpDemoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final MvpDemoService demoService;

    public BookingController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/checkout-preview")
    public Object checkoutPreview(@RequestBody ApiRequests.PromotionApply request) {
        return demoService.previewCheckout(request);
    }

    @PostMapping
    public Object createBooking(@RequestBody ApiRequests.BookingCreate request) {
        return demoService.createBooking(request);
    }

    @GetMapping
    public Object bookings(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return demoService.bookings(customerId, date);
    }

    @GetMapping("/{bookingId}")
    public Object bookingDetail(@PathVariable Long bookingId) {
        return demoService.bookingDetail(bookingId);
    }

    @PutMapping("/{bookingId}/status")
    public Object updateStatus(@PathVariable Long bookingId, @RequestBody ApiRequests.BookingStatusUpdate request) {
        return demoService.updateBookingStatus(bookingId, request);
    }
}
