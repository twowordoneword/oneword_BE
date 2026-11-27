package com.example.musing_BE.common.config;

import com.example.musing_BE.auth.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductionConfigGuard — 운영 설정 점검")
class ProductionConfigGuardTest {

    private static final String STRONG_SECRET = "Zx8pQ2vN7rT4wY1mK6sB3dF9hJ0lC5gA";
    private static final String STRONG_ADMIN = "a".repeat(32);
    private static final List<String> ORIGINS = List.of("https://musing.app");

    @Test
    @DisplayName("안전한 설정이면 통과한다")
    void passesSafeConfig() {
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800), ORIGINS, STRONG_ADMIN)).isEmpty();
        // 관리자 토큰이 비어 있으면 관리자 API가 잠기므로 허용한다
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800), ORIGINS, "")).isEmpty();
        // 서브도메인 와일드카드는 허용
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800),
                List.of("https://*.musing.app"), STRONG_ADMIN)).isEmpty();
    }

    @Test
    @DisplayName("공개된 개발용 시크릿이나 짧은 시크릿은 거부한다")
    void rejectsWeakSecret() {
        assertThat(ProductionConfigGuard.check(
                auth("local-dev-jwt-secret-change-this-at-least-32-bytes", 1800), ORIGINS, ""))
                .anyMatch(p -> p.contains("APP_JWT_SECRET"));
        assertThat(ProductionConfigGuard.check(auth("short", 1800), ORIGINS, ""))
                .anyMatch(p -> p.contains("APP_JWT_SECRET"));
    }

    @Test
    @DisplayName("모든 출처를 허용하는 CORS는 거부한다")
    void rejectsWildcardCors() {
        for (String origin : List.of("*", "https://*", "http://*:3000")) {
            assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800), List.of(origin), ""))
                    .as(origin)
                    .anyMatch(p -> p.contains("CORS"));
        }
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800), List.of(), ""))
                .anyMatch(p -> p.contains("CORS"));
    }

    @Test
    @DisplayName("액세스 토큰 수명이 너무 길면 거부한다")
    void rejectsLongAccessToken() {
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 86400), ORIGINS, ""))
                .anyMatch(p -> p.contains("APP_ACCESS_TOKEN_SECONDS"));
    }

    @Test
    @DisplayName("짧은 관리자 토큰은 거부한다")
    void rejectsShortAdminToken() {
        assertThat(ProductionConfigGuard.check(auth(STRONG_SECRET, 1800), ORIGINS, "admin123"))
                .anyMatch(p -> p.contains("ADMIN_TOKEN"));
    }

    @Test
    @DisplayName("문제가 있으면 생성 단계에서 예외로 기동을 막는다")
    void constructorFailsFast() {
        assertThatThrownBy(() -> new ProductionConfigGuard(auth("short", 1800), List.of("*"), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET")
                .hasMessageContaining("CORS");
    }

    private AuthProperties auth(String secret, long accessSeconds) {
        return new AuthProperties(secret, accessSeconds, 1209600,
                List.of("g"), List.of("a"), List.of("k"), "n", "ns");
    }
}
