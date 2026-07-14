package com.swp391.backend.repository;

import com.swp391.backend.entity.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findByEmail(String email);

    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findByPhone(String phone);
}

