package com.example.musing_BE.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user,
        boolean isNewUser
) {}
