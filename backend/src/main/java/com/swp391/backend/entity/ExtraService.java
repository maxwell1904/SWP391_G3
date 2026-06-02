package com.swp391.backend.entity;

import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.ServiceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "extra_service")
public class ExtraService extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extra_service_id")
    private Long extraServiceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType = ServiceType.rental;

    private String description;

    @Column(name = "unit_name", nullable = false, length = 30)
    private String unitName = "item";

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "max_quantity_per_booking")
    private Integer maxQuantityPerBooking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommonStatus status = CommonStatus.active;
}
