package com.swp391.backend.repository;

import com.swp391.backend.entity.FootballField;
import com.swp391.backend.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FootballFieldRepository extends JpaRepository<FootballField, Long> {
    List<FootballField> findByStatus(CommonStatus status);
}
