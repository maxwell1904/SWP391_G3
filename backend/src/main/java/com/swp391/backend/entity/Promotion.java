package com.swp391.backend.entity;

import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promotion")
public class Promotion extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "promotion_code", nullable = false, unique = true, length = 50)
    private String promotionCode;

    @Column(name = "promotion_name", nullable = false, length = 120)
    private String promotionName;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_booking_amount", precision = 12, scale = 2)
    private BigDecimal minBookingAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_field_type_id")
    private FieldType applicableFieldType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_membership_level_id")
    private MembershipLevel applicableMembershipLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_extra_service_id")
    private ExtraService applicableExtraService;

    @Column(name = "applicable_day_type", length = 30)
    private String applicableDayType;

    @Column(name = "applicable_start_time")
    private LocalTime applicableStartTime;

    @Column(name = "applicable_end_time")
    private LocalTime applicableEndTime;

    @Column(nullable = false)
    private boolean stackable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommonStatus status = CommonStatus.active;
}
