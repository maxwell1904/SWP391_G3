package com.swp391.backend.repository;

import com.swp391.backend.entity.MembershipLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipLevelRepository extends JpaRepository<MembershipLevel, Long> {
    List<MembershipLevel> findAllByOrderByDisplayOrderAsc();
}
