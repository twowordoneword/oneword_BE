package com.example.musing_BE.recommendation.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EmotionCalibrator — 목표 좌표를 실제 곡 분포로 옮긴다")
class EmotionCalibratorTest {

    private EmotionCalibrator calibrator;

    // 테스트가 쓰는 구간은 여기서 직접 정한다.
    // 운영 기본값(실측치)은 곡이 늘면 바뀌므로, 그 값에 기대면 테스트가 계속 깨진다.
    private static final double V_MIN = 0.30, V_MAX = 0.75;
    private static final double A_MIN = 0.10, A_MAX = 0.95;

    @BeforeEach
    void setUp() {
        calibrator = new EmotionCalibrator();
        ReflectionTestUtils.setField(calibrator, "enabled", true);
        ReflectionTestUtils.setField(calibrator, "valenceMin", V_MIN);
        ReflectionTestUtils.setField(calibrator, "valenceMax", V_MAX);
        ReflectionTestUtils.setField(calibrator, "arousalMin", A_MIN);
        ReflectionTestUtils.setField(calibrator, "arousalMax", A_MAX);
    }

    @Test
    @DisplayName("0~1 좌표를 관측 구간으로 선형 재매핑한다")
    void mapsIntoObservedRange() {
        assertThat(calibrator.calibrate(new EmotionPoint(0.15, 0.25)).valence())
                .isCloseTo(V_MIN + 0.15 * (V_MAX - V_MIN), within(1e-9));   // 슬픔
        assertThat(calibrator.calibrate(new EmotionPoint(0.90, 0.75)).valence())
                .isCloseTo(V_MIN + 0.90 * (V_MAX - V_MIN), within(1e-9));   // 기쁨
    }

    @Test
    @DisplayName("양 끝은 관측 구간의 경계가 된다")
    void endpointsMapToBounds() {
        EmotionPoint low = calibrator.calibrate(new EmotionPoint(0.0, 0.0));
        EmotionPoint high = calibrator.calibrate(new EmotionPoint(1.0, 1.0));

        assertThat(low.valence()).isCloseTo(V_MIN, within(1e-9));
        assertThat(low.arousal()).isCloseTo(A_MIN, within(1e-9));
        assertThat(high.valence()).isCloseTo(V_MAX, within(1e-9));
        assertThat(high.arousal()).isCloseTo(A_MAX, within(1e-9));
    }

    @Test
    @DisplayName("순서는 그대로 유지된다 (기분 구분이 살아 있다)")
    void preservesOrdering() {
        double sad   = calibrator.calibrate(new EmotionPoint(0.15, 0.25)).valence();
        double gloom = calibrator.calibrate(new EmotionPoint(0.20, 0.30)).valence();
        double calm  = calibrator.calibrate(new EmotionPoint(0.70, 0.25)).valence();
        double joy   = calibrator.calibrate(new EmotionPoint(0.90, 0.75)).valence();

        assertThat(sad).isLessThan(gloom);
        assertThat(gloom).isLessThan(calm);
        assertThat(calm).isLessThan(joy);
    }

    @Test
    @DisplayName("보정을 끄면 원래 좌표를 그대로 준다")
    void disabledPassesThrough() {
        ReflectionTestUtils.setField(calibrator, "enabled", false);

        EmotionPoint p = calibrator.calibrate(new EmotionPoint(0.15, 0.25));

        assertThat(p.valence()).isEqualTo(0.15);
        assertThat(p.arousal()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("설정이 뒤집혀 있으면 보정하지 않는다 (잘못된 설정 방어)")
    void invalidRangeIsIgnored() {
        ReflectionTestUtils.setField(calibrator, "valenceMin", 0.9);
        ReflectionTestUtils.setField(calibrator, "valenceMax", 0.1);

        assertThat(calibrator.calibrate(new EmotionPoint(0.5, 0.5)).valence()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("보정 후에도 슬픔과 우울은 붙어 있다 (좌표 자체를 벌려야 함)")
    void sadAndGloomStillClose() {
        EmotionPoint sad   = calibrator.calibrate(new EmotionPoint(0.15, 0.25));
        EmotionPoint gloom = calibrator.calibrate(new EmotionPoint(0.20, 0.30));

        // 보정은 분포를 맞출 뿐 기분 사이 간격을 넓혀주지 않는다 — 별도 튜닝 과제
        assertThat(sad.distanceTo(gloom)).isLessThan(0.1);
    }

    @Test
    @DisplayName("운영 기본값도 유효 범위 안이고 순서를 지킨다")
    void productionDefaultsAreSane() {
        EmotionCalibrator prod = new EmotionCalibrator();   // 필드 초기값 = 운영 기본값

        EmotionPoint sad = prod.calibrate(new EmotionPoint(0.15, 0.25));
        EmotionPoint joy = prod.calibrate(new EmotionPoint(0.90, 0.75));

        assertThat(sad.valence()).isBetween(0.0, 1.0);
        assertThat(joy.valence()).isBetween(0.0, 1.0);
        assertThat(sad.valence()).isLessThan(joy.valence());
    }
}
