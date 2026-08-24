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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class AppJwtService {
    private final SecretKey secretKey;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AppJwtService(AuthProperties authProperties, Clock kstClock) {
        this.authProperties = authProperties;
        this.clock = kstClock;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair issueTokenPair(Long userId) {
        String accessToken = issueToken(userId, "access", authProperties.accessTokenSeconds());
        String refreshToken = issueToken(userId, "refresh", authProperties.refreshTokenSeconds());
        // 만료 시각은 DB에 LocalDateTime으로 저장되고 비교도 LocalDateTime으로 한다.
        // 저장·비교가 같은 시계(kstClock)를 쓰지 않으면 서버 타임존만큼 수명이 어긋난다.
        LocalDateTime refreshExpiresAt = LocalDateTime.now(clock).plusSeconds(authProperties.refreshTokenSeconds());
        return new TokenPair(accessToken, refreshToken, authProperties.accessTokenSeconds(), refreshExpiresAt);
    }

    public Long parseAccessToken(String token) {
        return subjectAsUserId(parseClaims(token, ErrorCode.UNAUTHORIZED), "access", ErrorCode.UNAUTHORIZED);
    }

    public Long parseRefreshToken(String token) {
        return subjectAsUserId(parseClaims(token, ErrorCode.INVALID_REFRESH_TOKEN), "refresh",
                ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private String issueToken(Long userId, String type, long expiresInSeconds) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    private Long subjectAsUserId(Claims claims, String expectedType, ErrorCode errorCode) {
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new BusinessException(errorCode);
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (RuntimeException e) {
            // subject가 숫자가 아니면 인증 실패다. 그냥 두면 필터 밖으로 새어 500이 된다.
            throw new BusinessException(errorCode);
        }
    }

    private Claims parseClaims(String token, ErrorCode errorCode) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(Instant.now(clock)))
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
