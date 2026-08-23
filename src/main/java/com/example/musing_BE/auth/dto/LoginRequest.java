package com.example.musing_BE.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String idToken,
        String nickname
) {}
