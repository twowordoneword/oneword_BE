package com.example.musing_BE.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthProperties — 소셜 client id 바인딩")
class AuthPropertiesTest {

    /**
     * 플랫폼마다 client id가 달라 aud도 달라지므로 여러 개를 등록할 수 있어야 한다.
     * 환경변수는 문자열 하나로 들어오니, 콤마 구분이 실제로 목록으로 쪼개지는지 확인한다.
     */
    @Test
    @DisplayName("콤마로 구분한 client id는 목록으로 쪼개진다")
    void bindsCommaSeparatedClientIds() {
        AuthProperties bound = bind(Map.of(
                "app.auth.jwt-secret", "test-jwt-secret-at-least-32-bytes",
                "app.auth.access-token-seconds", "3600",
                "app.auth.refresh-token-seconds", "1209600",
                "app.auth.google-client-id", "web.apps.googleusercontent.com",
                "app.auth.apple-client-id", "com.musing.app,com.musing.web",
                "app.auth.kakao-client-id", "native-app-key",
                "app.auth.naver-client-id", "naver-id",
                "app.auth.naver-client-secret", "naver-secret"
        ));

        assertThat(bound.appleClientId()).containsExactly("com.musing.app", "com.musing.web");
        assertThat(bound.googleClientId()).containsExactly("web.apps.googleusercontent.com");
        assertThat(bound.kakaoClientId()).containsExactly("native-app-key");
    }

    private AuthProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("app.auth", AuthProperties.class)
                .get();
    }
}
