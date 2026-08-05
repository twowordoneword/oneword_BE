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

/**
 * 배치 수동 트리거.
 * <p>용도: 개발 중 즉시 실행 + <b>배포 직후 운영 DB 초기 적재</b>(로컬 배치는 로컬 DB만 채우므로).
 * <p>⚠️ 인증 체계가 아직 없어 공유 토큰 헤더로 보호한다. 인증 도입 후 관리자 권한 검사로 교체할 것.
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

    private void assertAdmin(String token) {
        if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
