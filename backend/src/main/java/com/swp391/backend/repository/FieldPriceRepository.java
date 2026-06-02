package com.swp391.backend.repository;

import com.swp391.backend.entity.FieldPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldPriceRepository extends JpaRepository<FieldPrice, Long> {
    List<FieldPrice> findByField_FieldId(Long fieldId);
}
