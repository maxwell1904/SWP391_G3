package com.swp391.backend.repository;

import com.swp391.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.swp391.backend.enums.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser_UserIdOrderByNotificationIdDesc(Long userId);
    boolean existsByBooking_BookingIdAndNotificationType(Long bookingId, NotificationType notificationType);
}
