package com.example.musing_BE.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 인증 설정.
 *
 * <p>{@code googleClientId}·{@code appleClientId}·{@code kakaoClientId}·{@code naverClientId}·
 * {@code naverClientSecret}는 <b>토큰 치환 공격</b>을 막기 위한 값이다. 소셜 토큰은
 * "우리 앱에 발급된 것"임을 증명해야 하며, 그렇지 않으면 제3자 앱이 자기 사용자에게서 받은
 * 토큰으로 우리 서비스에 로그인할 수 있다.
 * 구글·애플·카카오는 id_token의 {@code aud}로, 네이버는 인가 코드를 우리 client_id/secret으로
 * 직접 교환해서 이를 보장한다.
 *
 * <p><b>client id가 목록인 이유:</b> 같은 제공자라도 플랫폼마다 다른 client id로 토큰이
 * 발급되고, 그 값이 그대로 {@code aud}에 담긴다. 예를 들어 애플은 iOS 네이티브 로그인이면
 * 앱의 Bundle ID, 웹·안드로이드면 Services ID다. 단일 값만 받으면 한쪽 플랫폼의 로그인이
 * 통째로 막힌다. 환경변수에 콤마로 구분해 넘기면 된다.
 *
 * <pre>APPLE_CLIENT_ID=com.musing.app,com.musing.web</pre>
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTokenSeconds,
        long refreshTokenSeconds,
        List<String> googleClientId,
        List<String> appleClientId,
        List<String> kakaoClientId,
        String naverClientId,
        String naverClientSecret
) {}
