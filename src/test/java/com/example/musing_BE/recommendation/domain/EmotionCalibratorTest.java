package com.example.musing_BE.recommendation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EmotionCalibrator — 목표 좌표를 실제 곡 분포로 옮긴다")
class EmotionCalibratorTest {

    private final EmotionCalibrator calibrator = new EmotionCalibrator();

    @Test
    @DisplayName("0~1 좌표를 관측 구간으로 선형 재매핑한다")
    void mapsIntoObservedRange() {
        // 슬픔 valence 0.15 → 0.30 + 0.15×0.45 = 0.3675
        EmotionPoint sad = calibrator.calibrate(new EmotionPoint(0.15, 0.25));
        assertThat(sad.valence()).isCloseTo(0.3675, within(1e-9));

        // 기쁨 valence 0.90 → 0.30 + 0.90×0.45 = 0.705
        EmotionPoint joy = calibrator.calibrate(new EmotionPoint(0.90, 0.75));
        assertThat(joy.valence()).isCloseTo(0.705, within(1e-9));
    }

    @Test
    @DisplayName("양 끝은 관측 구간의 경계가 된다")
    void endpointsMapToBounds() {
        assertThat(calibrator.calibrate(new EmotionPoint(0.0, 0.0)).valence()).isCloseTo(0.30, within(1e-9));
        assertThat(calibrator.calibrate(new EmotionPoint(1.0, 1.0)).valence()).isCloseTo(0.75, within(1e-9));
        assertThat(calibrator.calibrate(new EmotionPoint(0.0, 0.0)).arousal()).isCloseTo(0.10, within(1e-9));
        assertThat(calibrator.calibrate(new EmotionPoint(1.0, 1.0)).arousal()).isCloseTo(0.95, within(1e-9));
    }

    @Test
    @DisplayName("순서는 그대로 유지된다 (기분 구분이 살아 있다)")
    void preservesOrdering() {
        EmotionPoint sad   = calibrator.calibrate(new EmotionPoint(0.15, 0.25));
        EmotionPoint gloom = calibrator.calibrate(new EmotionPoint(0.20, 0.30));
        EmotionPoint calm  = calibrator.calibrate(new EmotionPoint(0.70, 0.25));
        EmotionPoint joy   = calibrator.calibrate(new EmotionPoint(0.90, 0.75));

        assertThat(sad.valence()).isLessThan(gloom.valence());
        assertThat(gloom.valence()).isLessThan(calm.valence());
        assertThat(calm.valence()).isLessThan(joy.valence());
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

        EmotionPoint p = calibrator.calibrate(new EmotionPoint(0.5, 0.5));

        assertThat(p.valence()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("보정 후에도 슬픔과 우울은 여전히 붙어 있다 (좌표 자체를 벌려야 함)")
    void sadAndGloomStillClose() {
        EmotionPoint sad   = calibrator.calibrate(new EmotionPoint(0.15, 0.25));
        EmotionPoint gloom = calibrator.calibrate(new EmotionPoint(0.20, 0.30));

        // 보정은 분포를 맞출 뿐 기분 사이 간격을 넓혀주지 않는다 — §11 튜닝 과제로 남는다
        assertThat(sad.distanceTo(gloom)).isLessThan(0.1);
    }
}
