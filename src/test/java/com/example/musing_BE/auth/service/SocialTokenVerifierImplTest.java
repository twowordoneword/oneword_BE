package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.config.AuthProperties;
import com.example.musing_BE.auth.domain.SocialLoginCommand;
import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SocialTokenVerifierImpl — 제공자별 설정 게이트")
class SocialTokenVerifierImplTest {

    /**
     * 키가 제공자별로 순차적으로 들어오는 동안 미설정 제공자가 서버 기동을 막으면 안 된다.
     * 대신 그 제공자 로그인은 확실히 거부돼야 한다 — 검증값 없이 통과시키는 일은 없어야 하므로.
     */
    @Test
    @DisplayName("설정이 없는 제공자는 외부 호출 전에 UNSUPPORTED_PROVIDER로 거절한다")
    void rejectsUnconfiguredProvider() {
        SocialTokenVerifierImpl verifier = verifierWith(List.of(), null, null);

        assertThatThrownBy(() -> verifier.verify(
                new SocialLoginCommand(SocialProvider.NAVER, "authorization-code", "state")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_PROVIDER));
    }

    @Test
    @DisplayName("설정된 제공자만 활성으로 본다")
    void reportsOnlyConfiguredProviders() {
        SocialTokenVerifierImpl verifier = verifierWith(List.of("kakao-app-key"), "naver-id", "naver-secret");

        assertThat(verifier.isConfigured(SocialProvider.KAKAO)).isTrue();
        assertThat(verifier.isConfigured(SocialProvider.NAVER)).isTrue();
        assertThat(verifier.isConfigured(SocialProvider.GOOGLE)).isFalse();
        assertThat(verifier.isConfigured(SocialProvider.APPLE)).isFalse();
    }

    /** 네이버는 인가 코드를 직접 교환하므로 secret 없이는 아무것도 못 한다. */
    @Test
    @DisplayName("네이버는 client secret이 없으면 비활성이다")
    void naverRequiresSecret() {
        assertThat(verifierWith(List.of(), "naver-id", null).isConfigured(SocialProvider.NAVER)).isFalse();
    }

    @Test
    @DisplayName("공백만 있는 설정은 설정된 것으로 보지 않는다")
    void blankValuesAreNotConfigured() {
        assertThat(verifierWith(List.of("  "), null, null).isConfigured(SocialProvider.KAKAO)).isFalse();
    }

    private SocialTokenVerifierImpl verifierWith(List<String> kakaoClientId, String naverId, String naverSecret) {
        return new SocialTokenVerifierImpl(new AuthProperties(
                "test-jwt-secret-at-least-32-bytes", 3600, 1209600,
                List.of(), List.of(), kakaoClientId, naverId, naverSecret));
    }
}
