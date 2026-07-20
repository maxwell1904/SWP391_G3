package com.swp391.backend.service;

import com.swp391.backend.entity.Booking;
import com.swp391.backend.enums.BookingStatus;
import com.swp391.backend.enums.NotificationType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Background maintenance for UC-43 (unpaid holds) and UC-58 (booking reminders). */
@Service
public class BookingMaintenanceScheduler {
    private final DemoSupportService support;

    public BookingMaintenanceScheduler(DemoSupportService support) {
        this.support = support;
    }

    @Scheduled(fixedDelayString = "${app.booking-maintenance-delay-ms:60000}")
    @Transactional
    public void maintainBookings() {
        expireUnpaidPendingBookings();
        sendUpcomingBookingReminders();
    }

    public int expireUnpaidPendingBookings() {
        long timeoutMinutes = support.settingDecimal("payment.pending_timeout_minutes", BigDecimal.valueOf(15)).longValue();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<Booking> candidates = support.bookingRepository.findByStatusIn(List.of(BookingStatus.pending));
        int expired = 0;
        for (Booking booking : candidates) {
            if (booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0 || booking.getCreatedAt() == null || booking.getCreatedAt().isAfter(cutoff)) continue;
            booking.setStatus(BookingStatus.expired);
            booking.setExpiredAt(LocalDateTime.now());
            support.notifyUser(booking.getCustomer(), booking, NotificationType.payment, "Booking hold expired",
                    "Booking " + booking.getBookingCode() + " was released because payment was not completed in time.");
            expired++;
        }
        return expired;
    }

    public int sendUpcomingBookingReminders() {
        long reminderHours = support.settingDecimal("notification.booking_reminder_hours", BigDecimal.valueOf(24)).longValue();
        long reminderWindowMinutes = Math.max(1, reminderHours) * 60;
        int sent = 0;
        for (Booking booking : support.bookingRepository.findByStatusIn(List.of(BookingStatus.pending, BookingStatus.confirmed))) {
            LocalDateTime start = LocalDateTime.of(booking.getSlot().getSlotDate(), booking.getSlot().getStartTime());
            long minutesUntil = Duration.between(LocalDateTime.now(), start).toMinutes();
            if (minutesUntil < 0 || minutesUntil > reminderWindowMinutes
                    || support.notificationRepository.existsByBooking_BookingIdAndNotificationType(booking.getBookingId(), NotificationType.booking_reminder)) continue;
            support.notifyUser(booking.getCustomer(), booking, NotificationType.booking_reminder, "Booking reminder",
                    "Reminder: " + booking.getBookingCode() + " starts at " + booking.getSlot().getStartTime() + " on " + booking.getSlot().getSlotDate() + ".");
            sent++;
        }
        return sent;
    }
}
