package com.swp391.backend.entity;

import com.swp391.backend.enums.CommonStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_level")
public class MembershipLevel extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_level_id")
    private Long membershipLevelId;

    @Column(name = "level_name", nullable = false, unique = true, length = 50)
    private String levelName;

    @Column(name = "required_completed_bookings", nullable = false)
    private int requiredCompletedBookings;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "benefit_description", columnDefinition = "text")
    private String benefitDescription;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "qualification_period", nullable = false, length = 20)
    private String qualificationPeriod = "lifetime";

    @Column(name = "required_consecutive_periods", nullable = false)
    private int requiredConsecutivePeriods = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommonStatus status = CommonStatus.active;
}
