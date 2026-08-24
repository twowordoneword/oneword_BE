package com.example.musing_BE.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정.
 *
 * <p>{@code kakaoAppId}·{@code naverClientId}·{@code naverClientSecret}는 <b>토큰 치환 공격</b>을
 * 막기 위한 값이다. 소셜 토큰은 "우리 앱에 발급된 것"임을 증명해야 하며, 그렇지 않으면 제3자 앱이
 * 자기 사용자에게서 받은 토큰으로 우리 서비스에 로그인할 수 있다.
 * 구글·애플은 id_token의 {@code aud}로, 카카오는 access_token_info의 {@code app_id}로,
 * 네이버는 인가 코드를 우리 client_id/secret으로 직접 교환해서 이를 보장한다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTokenSeconds,
        long refreshTokenSeconds,
        String googleClientId,
        String appleClientId,
        String kakaoAppId,
        String naverClientId,
        String naverClientSecret
) {}
