package com.swp391.backend.repository;

import com.swp391.backend.entity.BookingServiceItem;
import com.swp391.backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingServiceItemRepository extends JpaRepository<BookingServiceItem, Long> {
    List<BookingServiceItem> findByBooking_BookingId(Long bookingId);
    void deleteByBooking_BookingId(Long bookingId);

    @Query("""
            select coalesce(sum(item.quantity), 0)
            from BookingServiceItem item
            where item.extraService.extraServiceId = :serviceId
              and item.booking.status in :statuses
              and item.booking.slot.slotDate = :slotDate
              and item.booking.slot.startTime < :endTime
              and item.booking.slot.endTime > :startTime
              and (:excludedBookingId is null or item.booking.bookingId <> :excludedBookingId)
            """)
    long sumReservedForOverlappingSlots(
            @Param("serviceId") Long serviceId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("excludedBookingId") Long excludedBookingId
    );
}
