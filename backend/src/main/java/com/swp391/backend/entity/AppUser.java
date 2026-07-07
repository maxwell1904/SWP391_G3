package com.swp391.backend.entity;

import com.swp391.backend.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, length = 120)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.active;

    @Column(name = "booking_restricted", nullable = false)
    private boolean bookingRestricted;

    @Column(name = "restriction_reason")
    private String restrictionReason;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Incremented whenever all active JWTs for this account must become invalid
     * (logout, password reset/change, or an administrator lock).  This keeps
     * the API stateless while still giving logout meaningful server-side effect.
     */
    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verification_token", length = 16)
    private String emailVerificationToken;

    @Column(name = "email_verification_sent_at")
    private LocalDateTime emailVerificationSentAt;

    @Column(name = "password_reset_token", length = 16)
    private String passwordResetToken;

    @Column(name = "password_reset_sent_at")
    private LocalDateTime passwordResetSentAt;
}
