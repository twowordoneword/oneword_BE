package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.config.AuthProperties;
import com.example.musing_BE.auth.domain.SocialLoginCommand;
import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.common.http.RestClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 소셜 자격증명 검증.
 *
 * <p><b>모든 제공자에 대해 "우리 앱에 발급된 자격증명인가"를 반드시 확인한다.</b>
 * 이 확인이 없으면 제3자가 자기 앱으로 모은 토큰을 그대로 우리 로그인에 던져
 * 남의 계정이 될 수 있다(토큰 치환 공격).
 * <ul>
 *   <li>구글·애플·카카오 — id_token의 {@code aud}가 우리 client id인지</li>
 *   <li>네이버 — OIDC를 제공하지 않아, 인가 코드를 우리 client_id/secret으로 직접 교환한다</li>
 * </ul>
 */
@Slf4j
@Component
public class SocialTokenVerifierImpl implements SocialTokenVerifier {

    private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String NAVER_USER_ME_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final AuthProperties authProperties;

    /**
     * OIDC 디스커버리는 네트워크를 타므로 <b>첫 사용 시점까지 미룬다.</b>
     * 생성자에서 받아오면 제공자 쪽이 잠깐 불안정하거나 오프라인일 때 앱이 아예 뜨지 않고,
     * 소셜 로그인을 쓰지 않는 테스트 컨텍스트도 매번 외부 호출을 하게 된다.
     */
    private final LazyJwtDecoder googleDecoder = new LazyJwtDecoder("https://accounts.google.com");
    private final LazyJwtDecoder appleDecoder = new LazyJwtDecoder("https://appleid.apple.com");
    private final LazyJwtDecoder kakaoDecoder = new LazyJwtDecoder("https://kauth.kakao.com");

    public SocialTokenVerifierImpl(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.restClient = RestClients.createDefault();
    }

    /**
     * 설정되지 않은 제공자는 여기서 끊는다.
     *
     * <p>키가 제공자별로 순차적으로 들어오는 동안(애플은 유료 프로그램 가입 전, 네이버는 검수 전)
     * 하나가 비었다고 서버 전체가 못 뜨면 나머지 로그인까지 막힌다. 대신 <b>비어 있는 제공자는
     * 로그인이 거부된다</b> — 검증값 없이 통과시키는 일은 없으므로 안전성은 그대로다.
     */
    @Override
    public SocialUserInfo verify(SocialLoginCommand command) {
        if (!isConfigured(command.provider())) {
            log.warn("설정되지 않은 제공자로 로그인 시도: {}", command.provider());
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        return switch (command.provider()) {
            case KAKAO -> verifyKakao(command.credential());
            case NAVER -> verifyNaver(command.credential(), command.state());
            case GOOGLE -> verifyGoogle(command.credential());
            case APPLE -> verifyApple(command.credential());
        };
    }

    /**
     * 카카오도 OpenID Connect를 지원하므로 구글·애플과 같은 방식으로 검증한다.
     *
     * <p>이전에는 access_token을 받아 {@code /v2/user/me}로 조회하고 {@code access_token_info}의
     * {@code app_id}를 대조했는데, 프론트(카카오 SDK)가 보내는 것은 OIDC id_token이라 서로 맞지 않았다.
     * id_token 검증으로 바꾸면 서명과 {@code aud}만으로 앱 바인딩이 증명되므로 추가 호출도 사라진다.
     */
    private SocialUserInfo verifyKakao(String idToken) {
        List<String> audiences = requireConfigured(authProperties.kakaoClientId(), "app.auth.kakao-client-id");
        Jwt jwt = decodeJwt(kakaoDecoder, idToken);
        validateAudience(jwt, audiences);
        return new SocialUserInfo(
                SocialProvider.KAKAO,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("nickname")
        );
    }

    /**
     * 네이버는 인가 코드를 받아 우리 client_id/secret으로 액세스 토큰을 직접 발급받는다.
     * 교환이 성공했다는 것 자체가 "이 코드는 우리 앱에 발급됐다"는 증거다.
     */
    private SocialUserInfo verifyNaver(String authorizationCode, String state) {
        String clientId = requireConfigured(authProperties.naverClientId(), "app.auth.naver-client-id");
        String clientSecret = requireConfigured(authProperties.naverClientSecret(), "app.auth.naver-client-secret");
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        try {
            URI tokenUri = UriComponentsBuilder.fromUriString(NAVER_TOKEN_URL)
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("code", authorizationCode)
                    .queryParam("state", state)
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            NaverTokenResponse token = restClient.get()
                    .uri(tokenUri)
                    .retrieve()
                    .body(NaverTokenResponse.class);

            if (token == null || token.accessToken == null || token.accessToken.isBlank()) {
                log.warn("Naver code exchange failed: {}", token == null ? "empty body" : token.error);
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }

            NaverUserMeResponse body = restClient.get()
                    .uri(NAVER_USER_ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken)
                    .retrieve()
                    .body(NaverUserMeResponse.class);

            if (body == null || body.response == null || body.response.id == null || body.response.id.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }

            return new SocialUserInfo(
                    SocialProvider.NAVER,
                    body.response.id,
                    body.response.email,
                    body.response.nickname
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Naver token verify failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private SocialUserInfo verifyGoogle(String idToken) {
        List<String> audiences = requireConfigured(authProperties.googleClientId(), "app.auth.google-client-id");
        Jwt jwt = decodeJwt(googleDecoder, idToken);
        validateAudience(jwt, audiences);
        return new SocialUserInfo(
                SocialProvider.GOOGLE,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")
        );
    }

    private SocialUserInfo verifyApple(String idToken) {
        List<String> audiences = requireConfigured(authProperties.appleClientId(), "app.auth.apple-client-id");
        Jwt jwt = decodeJwt(appleDecoder, idToken);
        validateAudience(jwt, audiences);
        return new SocialUserInfo(SocialProvider.APPLE, jwt.getSubject(), jwt.getClaimAsString("email"), null);
    }

    private Jwt decodeJwt(LazyJwtDecoder decoder, String token) {
        try {
            return decoder.get().decode(token);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    /** 플랫폼마다 client id가 달라 aud도 달라진다. 등록된 것 중 하나라도 맞으면 통과. */
    private void validateAudience(Jwt jwt, List<String> expectedAudiences) {
        List<String> audience = jwt.getAudience();
        if (audience == null || expectedAudiences.stream().noneMatch(audience::contains)) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    public boolean isConfigured(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> hasValue(authProperties.kakaoClientId());
            case GOOGLE -> hasValue(authProperties.googleClientId());
            case APPLE -> hasValue(authProperties.appleClientId());
            // 네이버는 인가 코드를 직접 교환하므로 secret까지 있어야 한다
            case NAVER -> hasValue(authProperties.naverClientId()) && hasValue(authProperties.naverClientSecret());
        };
    }

    /**
     * 어떤 제공자가 켜져 있는지 기동 시 남긴다.
     *
     * <p>미설정 제공자를 400으로 거절하다 보면 <b>설정을 깜빡한 것</b>과 <b>일부러 끈 것</b>이
     * 구분되지 않는다. 기동 로그에 찍어 두면 배포 직후 바로 눈에 띈다.
     */
    @PostConstruct
    void logConfiguredProviders() {
        List<SocialProvider> enabled = Arrays.stream(SocialProvider.values()).filter(this::isConfigured).toList();
        List<SocialProvider> disabled = Arrays.stream(SocialProvider.values()).filter(p -> !isConfigured(p)).toList();
        log.info("소셜 로그인 활성: {}", enabled.isEmpty() ? "없음" : enabled);
        if (!disabled.isEmpty()) {
            log.warn("소셜 로그인 비활성(설정 없음): {} — 의도한 것이 아니면 환경변수를 확인할 것", disabled);
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasValue(List<String> values) {
        return values != null && values.stream().anyMatch(this::hasValue);
    }

    /**
     * {@link #verify}에서 이미 설정 여부를 걸렀으므로 여기 도달하면 값이 있다.
     * 남겨 두는 것은 호출 순서가 바뀌었을 때를 대비한 안전장치다.
     */
    private String requireConfigured(String value, String propertyName) {
        if (!hasValue(value)) {
            log.error("필수 설정이 비어 있습니다: {}", propertyName);
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        return value.trim();
    }

    private List<String> requireConfigured(List<String> values, String propertyName) {
        List<String> configured = values == null ? List.of() : values.stream()
                .filter(this::hasValue)
                .map(String::trim)
                .toList();
        if (configured.isEmpty()) {
            log.error("필수 설정이 비어 있습니다: {}", propertyName);
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        return configured;
    }

    /** 첫 호출에서 한 번만 디스커버리하고 결과를 재사용한다. */
    private static final class LazyJwtDecoder {
        private final String issuer;
        private volatile JwtDecoder delegate;

        private LazyJwtDecoder(String issuer) {
            this.issuer = issuer;
        }

        JwtDecoder get() {
            JwtDecoder local = delegate;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                if (delegate == null) {
                    try {
                        delegate = JwtDecoders.fromIssuerLocation(issuer);
                    } catch (Exception e) {
                        log.warn("OIDC 디스커버리 실패 ({}): {}", issuer, e.getMessage());
                        throw new BusinessException(ErrorCode.EXTERNAL_API_UNAVAILABLE);
                    }
                }
                return delegate;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NaverTokenResponse {
        @JsonProperty("access_token")
        public String accessToken;
        public String error;
        @JsonProperty("error_description")
        public String errorDescription;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NaverUserMeResponse {
        public NaverUserResponse response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NaverUserResponse {
        public String id;
        public String email;
        public String nickname;
    }
}
