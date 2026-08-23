package com.example.musing_BE.auth.controller;

import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.auth.repository.RefreshTokenRepository;
import com.example.musing_BE.auth.service.RefreshTokenHasher;
import com.example.musing_BE.auth.service.SocialTokenVerifier;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.diary.repository.DiaryTrackRepository;
import com.example.musing_BE.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인증 API 통합 테스트")
class AuthIntegrationTest {

    private static final String INVALID_SOCIAL_TOKEN = "invalid-social-token";

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DiaryRepository diaryRepository;
    @Autowired
    DiaryTrackRepository diaryTrackRepository;
    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    RefreshTokenHasher refreshTokenHasher;
    @MockitoBean
    SocialTokenVerifier socialTokenVerifier;

    @BeforeEach
    void setUp() {
        given(socialTokenVerifier.verify(any(SocialProvider.class), anyString()))
                .willAnswer(invocation -> {
                    SocialProvider provider = invocation.getArgument(0, SocialProvider.class);
                    String token = invocation.getArgument(1, String.class);
                    if (INVALID_SOCIAL_TOKEN.equals(token)) {
                        throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
                    }
                    String providerId = token.replace("token-", "");
                    return new SocialUserInfo(
                            provider,
                            providerId,
                            providerId + "@musing.app",
                            "social-" + providerId
                    );
                });
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        diaryTrackRepository.deleteAll();
        diaryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("인증 없이 보호 API 호출 시 401을 반환한다")
    void unauthorizedWhenNoBearerToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증 없이 곡 검색 API 호출 시 401을 반환한다")
    void trackSearchUnauthorizedWhenNoBearerToken() throws Exception {
        mvc.perform(get("/api/v1/tracks/search").param("q", "아이유"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그인 성공 후 /auth/me에서 본인 프로필을 조회할 수 있다")
    void loginAndMeSuccess() throws Exception {
        LoginTokens tokens = login("me-user", "door", "kakao");

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokens.userId()))
                .andExpect(jsonPath("$.data.nickname").value("door"))
                .andExpect(jsonPath("$.data.provider").value("kakao"));
    }

    @Test
    @DisplayName("유효하지 않은 소셜 토큰이면 로그인에 실패한다")
    void loginFailsWithInvalidSocialToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"kakao","idToken":"%s"}
                                """.formatted(INVALID_SOCIAL_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SOCIAL_TOKEN"));
    }

    @Test
    @DisplayName("네이버 provider로도 로그인할 수 있다")
    void naverLoginSuccess() throws Exception {
        LoginTokens tokens = login("naver-user", "door", "naver");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("리프레시 토큰은 1회 사용 후 회전되며 재사용이 차단된다")
    void refreshTokenRotatesAndOldTokenIsRejected() throws Exception {
        LoginTokens tokens = login("refresh-user", null, "kakao");

        String firstRefreshResponse = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedRefreshToken = JsonPath.read(firstRefreshResponse, "$.data.refreshToken");
        assertThat(rotatedRefreshToken).isNotEqualTo(tokens.refreshToken());
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(tokens.refreshToken()))).isEmpty();

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃 이후 기존 리프레시 토큰으로 재발급할 수 없다")
    void logoutRevokesRefreshTokens() throws Exception {
        LoginTokens tokens = login("logout-user", null, "kakao");

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("회원 탈퇴 시 사용자 일기, 곡 연결, 리프레시 토큰이 삭제된다")
    void withdrawDeletesUserData() throws Exception {
        LoginTokens tokens = login("withdraw-user", null, "kakao");

        LocalDate diaryDate = LocalDate.now().minusDays(1);
        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date":"%s",
                                  "title":"탈퇴 테스트",
                                  "body":"본문",
                                  "mood":"평온",
                                  "weather":"맑음",
                                  "myTrack":{"name":"My Song","artist":"My Artist"},
                                  "todayTrack":{"name":"Reco Song","artist":"Reco Artist"}
                                }
                                """.formatted(diaryDate)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findById(tokens.userId())).isPresent();
        assertThat(diaryRepository.countByUserId(tokens.userId())).isEqualTo(1);
        assertThat(diaryTrackRepository.countByDiaryUserId(tokens.userId())).isEqualTo(2);
        assertThat(refreshTokenRepository.countByUserId(tokens.userId())).isEqualTo(1);

        mvc.perform(delete("/api/v1/auth/withdraw")
                        .header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(tokens.userId())).isEmpty();
        assertThat(diaryRepository.countByUserId(tokens.userId())).isZero();
        assertThat(diaryTrackRepository.countByDiaryUserId(tokens.userId())).isZero();
        assertThat(refreshTokenRepository.countByUserId(tokens.userId())).isZero();
    }

    private LoginTokens login(String tokenSuffix, String nickname, String provider) throws Exception {
        String requestBody = nickname == null
                ? """
                {"provider":"%s","idToken":"token-%s"}
                """.formatted(provider, tokenSuffix)
                : """
                {"provider":"%s","idToken":"token-%s","nickname":"%s"}
                """.formatted(provider, tokenSuffix, nickname);

        String content = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number userIdValue = JsonPath.read(content, "$.data.user.id");
        String accessToken = JsonPath.read(content, "$.data.accessToken");
        String refreshToken = JsonPath.read(content, "$.data.refreshToken");
        return new LoginTokens(userIdValue.longValue(), accessToken, refreshToken);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record LoginTokens(Long userId, String accessToken, String refreshToken) {
    }
}
