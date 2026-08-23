package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.config.AuthProperties;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class AppJwtService {
    private final SecretKey secretKey;
    private final AuthProperties authProperties;

    public AppJwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair issueTokenPair(Long userId) {
        String accessToken = issueToken(userId, "access", authProperties.accessTokenSeconds());
        String refreshToken = issueToken(userId, "refresh", authProperties.refreshTokenSeconds());
        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                Instant.now().plusSeconds(authProperties.refreshTokenSeconds()),
                ZoneOffset.UTC
        );
        return new TokenPair(accessToken, refreshToken, authProperties.accessTokenSeconds(), refreshExpiresAt);
    }

    public Long parseAccessToken(String token) {
        Claims claims = parseClaims(token, ErrorCode.UNAUTHORIZED);
        if (!"access".equals(claims.get("type", String.class))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(claims.getSubject());
    }

    public Long parseRefreshToken(String token) {
        Claims claims = parseClaims(token, ErrorCode.INVALID_REFRESH_TOKEN);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return Long.parseLong(claims.getSubject());
    }

    private String issueToken(Long userId, String type, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token, ErrorCode errorCode) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BusinessException(errorCode);
        }
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            long accessExpiresIn,
            LocalDateTime refreshExpiresAt
    ) {}
}
