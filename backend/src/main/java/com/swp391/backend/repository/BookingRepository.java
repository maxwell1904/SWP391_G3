package com.swp391.backend.repository;

import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsBySlot(Slot slot);
    boolean existsBySlotAndStatusIn(Slot slot, Collection<BookingStatus> statuses);
    List<Booking> findByCustomer_UserIdOrderByBookingIdDesc(Long customerId);
    List<Booking> findBySlot_SlotDateOrderBySlot_StartTimeAsc(LocalDate slotDate);
    List<Booking> findByStatusIn(Collection<BookingStatus> statuses);
}
