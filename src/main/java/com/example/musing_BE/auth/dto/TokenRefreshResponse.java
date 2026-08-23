package com.example.musing_BE.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
