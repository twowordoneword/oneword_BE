package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.config.AuthProperties;
import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SocialTokenVerifierImpl implements SocialTokenVerifier {
    private final RestClient restClient;
    private final JwtDecoder googleDecoder;
    private final JwtDecoder appleDecoder;
    private final AuthProperties authProperties;

    public SocialTokenVerifierImpl(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.restClient = RestClient.create();
        this.googleDecoder = JwtDecoders.fromIssuerLocation("https://accounts.google.com");
        this.appleDecoder = JwtDecoders.fromIssuerLocation("https://appleid.apple.com");
    }

    @Override
    public SocialUserInfo verify(SocialProvider provider, String token) {
        return switch (provider) {
            case KAKAO -> verifyKakao(token);
            case NAVER -> verifyNaver(token);
            case GOOGLE -> verifyGoogle(token);
            case APPLE -> verifyApple(token);
        };
    }

    private SocialUserInfo verifyKakao(String accessToken) {
        try {
            KakaoUserMeResponse body = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserMeResponse.class);

            if (body == null || body.id == null) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }
            String email = body.kakaoAccount != null ? body.kakaoAccount.email : null;
            String nickname = body.kakaoAccount != null && body.kakaoAccount.profile != null
                    ? body.kakaoAccount.profile.nickname : null;

            return new SocialUserInfo(SocialProvider.KAKAO, String.valueOf(body.id), email, nickname);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Kakao token verify failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private SocialUserInfo verifyGoogle(String idToken) {
        Jwt jwt = decodeJwt(googleDecoder, idToken);
        validateAudience(jwt, authProperties.googleClientId());
        String providerId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nickname = jwt.getClaimAsString("name");
        return new SocialUserInfo(SocialProvider.GOOGLE, providerId, email, nickname);
    }

    private SocialUserInfo verifyNaver(String accessToken) {
        try {
            NaverUserMeResponse body = restClient.get()
                    .uri("https://openapi.naver.com/v1/nid/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
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

    private SocialUserInfo verifyApple(String idToken) {
        Jwt jwt = decodeJwt(appleDecoder, idToken);
        validateAudience(jwt, authProperties.appleClientId());
        String providerId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        return new SocialUserInfo(SocialProvider.APPLE, providerId, email, null);
    }

    private Jwt decodeJwt(JwtDecoder decoder, String token) {
        try {
            return decoder.decode(token);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private void validateAudience(Jwt jwt, String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (jwt.getAudience() == null || jwt.getAudience().stream().noneMatch(expectedAudience::equals)) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KakaoUserMeResponse {
        public Long id;
        @JsonProperty("kakao_account")
        public KakaoAccount kakaoAccount;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KakaoAccount {
        public String email;
        public KakaoProfile profile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KakaoProfile {
        public String nickname;
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
