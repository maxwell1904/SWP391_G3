package com.swp391.backend.repository;

import com.swp391.backend.entity.ExtraService;
import com.swp391.backend.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraServiceRepository extends JpaRepository<ExtraService, Long> {
    List<ExtraService> findByStatus(CommonStatus status);
}
