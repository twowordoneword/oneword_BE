package com.example.musing_BE.track.controller;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.track.batch.ChartCollectService;
import com.example.musing_BE.track.batch.FeatureFillService;
import com.example.musing_BE.track.batch.SurveyCollectService;
import com.example.musing_BE.track.dto.CollectResult;
import com.example.musing_BE.track.dto.FeatureFillResult;
import com.example.musing_BE.track.dto.SurveyCollectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 배치 수동 트리거.
 * <p>용도: 개발 중 즉시 실행 + <b>배포 직후 운영 DB 초기 적재</b>(로컬 배치는 로컬 DB만 채우므로).
 *
 * <p>이 엔드포인트는 사용자 세션이 없는 곳(운영 스크립트·수동 호출)에서 부르므로 사용자 JWT를
 * 요구하지 않는다({@code SecurityConfig}에서 {@code /api/v1/admin/**}를 permitAll).
 * 보호는 아래 {@code X-Admin-Token} 공유 토큰이 단독으로 맡으며, 토큰이 설정돼 있지 않으면
 * 항상 거부한다.
 *
 * <p>관리자 역할(role) 개념이 생기면 공유 토큰 대신 권한 검사로 바꿀 것.
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
@RequiredArgsConstructor
public class AdminBatchController {

    private final ChartCollectService chartCollectService;
    private final SurveyCollectService surveyCollectService;
    private final FeatureFillService featureFillService;

    @Value("${musing.admin.token:}")
    private String adminToken;

    /** 설문에서 모은 가수 목록으로 후보 풀 수집. 가수 1명당 약 6초(속도 제한). */
    @PostMapping("/survey")
    public ApiResponse<CollectResult> collectSurvey(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody SurveyCollectRequest req) {
        assertAdmin(token);
        return ApiResponse.ok(
                surveyCollectService.collect(req.artists(), req.storefront(), req.songsPerArtist()));
    }

    /** 차트에서 후보 풀 수집 (차트 → 아티스트 카탈로그 확장). 수 분~수십 분 소요. */
    @PostMapping("/chart")
    public ApiResponse<CollectResult> collectChart(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        assertAdmin(token);
        return ApiResponse.ok(chartCollectService.collect());
    }

    /** 감정값이 비어 있는 곡을 FreqBlog으로 채운다. 실행당 상한은 설정값. */
    @PostMapping("/features")
    public ApiResponse<FeatureFillResult> fillFeatures(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        assertAdmin(token);
        return ApiResponse.ok(featureFillService.fill());
    }

    /**
     * 공유 토큰 검사.
     *
     * <p>비교에 {@code String.equals} 대신 {@link MessageDigest#isEqual}을 쓴다.
     * {@code equals}는 첫 글자가 다르면 즉시 false를 내므로, 응답 시간 차이로 토큰을 한 글자씩
     * 알아내는 <b>타이밍 공격</b>에 이론상 노출된다. {@code isEqual}은 길이·내용과 무관하게
     * 항상 같은 시간이 걸린다.
     *
     * <p>설정이 비어 있으면 항상 거부한다 — 환경변수를 깜빡했을 때 배치가 무방비로 열리는 것보다
     * 잠기는 편이 안전하다.
     */
    private void assertAdmin(String token) {
        if (adminToken == null || adminToken.isBlank() || token == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        byte[] expected = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
