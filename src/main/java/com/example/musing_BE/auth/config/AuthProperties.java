package com.example.musing_BE.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTokenSeconds,
        long refreshTokenSeconds,
        String googleClientId,
        String appleClientId
) {}
