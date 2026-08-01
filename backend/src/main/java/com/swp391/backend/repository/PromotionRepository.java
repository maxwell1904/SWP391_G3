package com.swp391.backend.repository;

import com.swp391.backend.entity.Promotion;
import com.swp391.backend.enums.CommonStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByStatus(CommonStatus status);
    Optional<Promotion> findByPromotionCodeIgnoreCase(String promotionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where lower(p.promotionCode) = lower(:code)")
    Optional<Promotion> findByPromotionCodeForUpdate(@Param("code") String code);
}
