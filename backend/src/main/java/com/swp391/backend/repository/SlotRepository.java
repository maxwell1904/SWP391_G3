package com.swp391.backend.repository;

import com.swp391.backend.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findBySlotDate(LocalDate slotDate);
    List<Slot> findByField_FieldIdAndSlotDate(Long fieldId, LocalDate slotDate);
    List<Slot> findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(LocalDate from, LocalDate to);
}
