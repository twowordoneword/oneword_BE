package com.example.musing_BE.track.controller;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.batch.ChartCollectService;
import com.example.musing_BE.track.batch.FeatureFillService;
import com.example.musing_BE.track.batch.SurveyCollectService;
import com.example.musing_BE.track.dto.CollectResult;
import com.example.musing_BE.track.dto.FeatureFillResult;
import com.example.musing_BE.track.dto.SurveyCollectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBatchController — 배치 트리거 보호")
class AdminBatchControllerTest {

    private static final String TOKEN = "test1234";

    @Mock ChartCollectService chartCollectService;
    @Mock SurveyCollectService surveyCollectService;
    @Mock FeatureFillService featureFillService;
    @InjectMocks AdminBatchController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "adminToken", TOKEN);
        given(featureFillService.fill()).willReturn(new FeatureFillResult(0, 0, 0, false, 0));
        given(chartCollectService.collect()).willReturn(new CollectResult(0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("올바른 토큰이면 배치를 실행한다")
    void runsWithValidToken() {
        controller.fillFeatures(TOKEN);

        verify(featureFillService).fill();
    }

    @Test
    @DisplayName("토큰이 틀리면 401")
    void rejectsWrongToken() {
        assertThatThrownBy(() -> controller.fillFeatures("wrong"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verify(featureFillService, never()).fill();
    }

    @Test
    @DisplayName("헤더가 없으면(null) 401")
    void rejectsMissingToken() {
        assertThatThrownBy(() -> controller.collectChart(null))
                .isInstanceOf(BusinessException.class);

        verify(chartCollectService, never()).collect();
    }

    @Test
    @DisplayName("길이만 같고 내용이 다른 토큰도 거부한다")
    void rejectsSameLengthToken() {
        assertThatThrownBy(() -> controller.fillFeatures("test0000"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("앞부분만 맞는 토큰도 거부한다 (부분 일치로 통과하지 않음)")
    void rejectsPrefixToken() {
        assertThatThrownBy(() -> controller.fillFeatures("test"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.fillFeatures("test1234extra"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("서버에 토큰 설정이 없으면 무조건 거부한다 (설정 누락 시 잠김)")
    void rejectsWhenServerTokenMissing() {
        ReflectionTestUtils.setField(controller, "adminToken", "");

        assertThatThrownBy(() -> controller.fillFeatures(TOKEN))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.fillFeatures(""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("설문 수집도 같은 검사를 거친다")
    void surveyRequiresToken() {
        SurveyCollectRequest req = new SurveyCollectRequest(List.of("아이유"), "kr", 10);

        assertThatThrownBy(() -> controller.collectSurvey("wrong", req))
                .isInstanceOf(BusinessException.class);

        verify(surveyCollectService, never()).collect(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
