package com.swp391.backend.repository;

import com.swp391.backend.entity.Promotion;
import com.swp391.backend.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByStatus(CommonStatus status);
    Optional<Promotion> findByPromotionCodeIgnoreCase(String promotionCode);
}
