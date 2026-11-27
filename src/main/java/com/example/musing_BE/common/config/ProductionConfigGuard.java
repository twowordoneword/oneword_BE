package com.example.musing_BE.common.config;

import com.example.musing_BE.auth.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 운영 환경에서 위험한 설정으로 서버가 뜨지 않게 막는다.
 *
 * <p>{@code local}·{@code test}를 제외한 모든 프로필(프로필 미지정 포함)을 운영으로 본다.
 * {@code prod}만 지정하면 프로필을 빼먹고 띄운 서버는 점검 없이 뜬다.
 *
 * <p>로컬용 기본값(공개된 JWT 시크릿, CORS {@code *})이 운영에 흘러들어가면 누구나 토큰을
 * 위조하거나 아무 사이트에서나 API를 부를 수 있다. 이런 실수는 런타임에는 아무 증상이 없어서
 * 눈치채기 어려우므로, <b>기동 시점에 실패시켜</b> 배포 단계에서 바로 드러나게 한다.
 */
@Slf4j
@Component
@Profile("!local & !test")
public class ProductionConfigGuard {

    /** 저장소에 공개된 개발·테스트용 시크릿. 운영에서 쓰면 토큰 위조가 가능하다. */
    static final Set<String> KNOWN_DEV_SECRETS = Set.of(
            "local-dev-jwt-secret-change-this-at-least-32-bytes",
            "test-jwt-secret-at-least-32-bytes"
    );
    static final int MIN_SECRET_BYTES = 32;
    /** 액세스 토큰은 서버에서 폐기할 수 없으므로(로그아웃·탈퇴 후에도 만료까지 유효) 수명을 제한한다. */
    static final long MAX_ACCESS_TOKEN_SECONDS = 3600;
    static final int MIN_ADMIN_TOKEN_LENGTH = 32;

    public ProductionConfigGuard(
            AuthProperties authProperties,
            @Value("${app.cors.allowed-origin-patterns}") List<String> corsPatterns,
            @Value("${musing.admin.token:}") String adminToken) {
        List<String> problems = check(authProperties, corsPatterns, adminToken);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "운영 설정이 안전하지 않아 기동을 중단합니다:\n - " + String.join("\n - ", problems));
        }
        log.info("운영 설정 점검 통과");
    }

    static List<String> check(AuthProperties auth, List<String> corsPatterns, String adminToken) {
        List<String> problems = new ArrayList<>();

        String secret = auth.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("APP_JWT_SECRET는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다");
        } else if (KNOWN_DEV_SECRETS.contains(secret.trim())) {
            problems.add("APP_JWT_SECRET가 저장소에 공개된 개발용 값입니다 — 새로 생성하세요");
        }

        if (auth.accessTokenSeconds() <= 0 || auth.accessTokenSeconds() > MAX_ACCESS_TOKEN_SECONDS) {
            problems.add("APP_ACCESS_TOKEN_SECONDS는 1~" + MAX_ACCESS_TOKEN_SECONDS + " 사이여야 합니다");
        }
        if (auth.refreshTokenSeconds() <= 0) {
            problems.add("APP_REFRESH_TOKEN_SECONDS는 0보다 커야 합니다");
        }

        List<String> origins = corsPatterns == null ? List.of() : corsPatterns.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            problems.add("CORS_ALLOWED_ORIGIN_PATTERNS가 비어 있습니다");
        }
        for (String origin : origins) {
            if (isWildcardOrigin(origin)) {
                problems.add("CORS_ALLOWED_ORIGIN_PATTERNS에 모든 출처 허용(" + origin + ")은 쓸 수 없습니다");
            }
        }

        // 비어 있으면 관리자 API가 항상 거부되므로 안전하다. 값이 있을 때만 강도를 본다.
        if (adminToken != null && !adminToken.isBlank() && adminToken.trim().length() < MIN_ADMIN_TOKEN_LENGTH) {
            problems.add("ADMIN_TOKEN은 " + MIN_ADMIN_TOKEN_LENGTH + "자 이상이어야 합니다");
        }
        return problems;
    }

    /** {@code *}, {@code https://*}처럼 호스트 전체가 와일드카드인 패턴. {@code https://*.musing.app}은 허용. */
    private static boolean isWildcardOrigin(String origin) {
        String host = origin.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        return host.equals("*") || host.startsWith("*:");
    }
}
