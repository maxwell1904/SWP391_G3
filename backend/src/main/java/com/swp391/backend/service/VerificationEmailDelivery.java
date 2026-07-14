package com.swp391.backend.service;

public record VerificationEmailDelivery(
        boolean sent,
        String status,
        String message
) {
}
