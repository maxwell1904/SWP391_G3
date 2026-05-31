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
@Table(name = "invoice")
public class Invoice extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @Column(name = "invoice_code", nullable = false, unique = true, length = 50)
    private String invoiceCode;

    @Column(name = "field_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fieldAmount = BigDecimal.ZERO;

    @Column(name = "service_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;
}
