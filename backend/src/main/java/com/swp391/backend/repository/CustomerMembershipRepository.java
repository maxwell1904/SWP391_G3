package com.swp391.backend.repository;

import com.swp391.backend.entity.CustomerMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerMembershipRepository extends JpaRepository<CustomerMembership, Long> {
    Optional<CustomerMembership> findByCustomer_UserId(Long customerId);
}
