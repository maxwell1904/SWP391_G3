package com.swp391.backend.repository;

import com.swp391.backend.entity.Booking;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.bookingId = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    boolean existsBySlot(Slot slot);
    boolean existsBySlotAndStatusIn(Slot slot, Collection<BookingStatus> statuses);
    List<Booking> findByCustomer_UserIdOrderByBookingIdDesc(Long customerId);
    List<Booking> findByCustomer_UserIdOrderByCreatedAtDescBookingIdDesc(Long customerId);
    List<Booking> findBySlot_SlotIdInAndStatusIn(Collection<Long> slotIds, Collection<BookingStatus> statuses);
    List<Booking> findBySlot_SlotDateOrderBySlot_StartTimeAsc(LocalDate slotDate);
    List<Booking> findByStatusIn(Collection<BookingStatus> statuses);
}
