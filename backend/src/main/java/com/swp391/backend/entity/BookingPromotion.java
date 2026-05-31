package com.swp391.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_promotion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_promotion", columnNames = {"booking_id", "promotion_id"})
})
public class BookingPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_promotion_id")
    private Long bookingPromotionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "promotion_code_snapshot", nullable = false, length = 50)
    private String promotionCodeSnapshot;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt = LocalDateTime.now();
}
