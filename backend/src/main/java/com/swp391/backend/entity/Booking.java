package com.swp391.backend.entity;

import com.swp391.backend.enums.BookingSource;
import com.swp391.backend.enums.BookingStatus;
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
@Table(name = "booking")
public class Booking extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private AppUser staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.pending;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", nullable = false)
    private BookingSource bookingSource = BookingSource.online;

    @Column(name = "field_price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fieldPriceAmount = BigDecimal.ZERO;

    @Column(name = "service_total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceTotalAmount = BigDecimal.ZERO;

    @Column(name = "promotion_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount = BigDecimal.ZERO;

    @Column(name = "membership_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal membershipDiscountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Column(name = "cancellation_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cancellationFeeAmount = BigDecimal.ZERO;

    @Column(name = "refundable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundableAmount = BigDecimal.ZERO;

    private String note;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
}
