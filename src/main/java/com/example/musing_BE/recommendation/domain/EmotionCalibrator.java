package com.example.musing_BE.recommendation.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 개념상의 감정 좌표를 <b>실제 곡들의 값 분포에 맞춰</b> 옮긴다.
 *
 * <p><b>왜 필요한가</b> — 기분 좌표(슬픔 0.15, 기쁨 0.90)는 0~1 전체를 쓴다는 전제로 정했는데,
 * 실제 분석 모델이 내놓는 valence는 훨씬 좁은 구간에 몰린다.
 * (실측: 김광석의 어두운 포크 곡들도 0.34~0.46, 최솟값이 0.3 근처)
 *
 * <pre>
 * 목표 좌표:  슬픔 0.15 ── 우울 0.20 ─────────────── 기쁨 0.90
 * 실제 곡들:              [0.30 ═══════════ 0.75]
 *             ↑ 이 구간엔 곡이 아예 없다
 * </pre>
 *
 * <p>이대로 두면 슬픔·우울이 똑같이 "가장 어두운 곡"을 고르게 되어 기분을 나눈 의미가 없어진다.
 * 그래서 목표 좌표를 관측 구간 안으로 <b>선형 재매핑</b>해 8개 기분이 서로 구분되게 만든다.
 *
 * <p>⚠️ 아래 기본값은 곡 수십 개로 관측한 잠정치다. 후보 풀이 커지면
 * {@code SELECT MIN(valence), MAX(valence) FROM tracks} 로 다시 재어 설정을 갱신할 것.
 */
@Slf4j
@Component
public class EmotionCalibrator {

    /** 끄면 개념 좌표를 그대로 쓴다(보정 전후 비교용). */
    @Value("${musing.recommendation.calibrate:true}")
    private boolean enabled = true;

    @Value("${musing.recommendation.valence-min:0.30}")
    private double valenceMin = 0.30;

    @Value("${musing.recommendation.valence-max:0.75}")
    private double valenceMax = 0.75;

    @Value("${musing.recommendation.arousal-min:0.10}")
    private double arousalMin = 0.10;

    @Value("${musing.recommendation.arousal-max:0.95}")
    private double arousalMax = 0.95;

    /**
     * 개념 좌표(0~1)를 관측 구간으로 옮긴다.
     *
     * <pre>
     * 슬픔 valence 0.15 → 0.30 + 0.15 × (0.75 − 0.30) = 0.3675
     * 기쁨 valence 0.90 → 0.30 + 0.90 × (0.75 − 0.30) = 0.7050
     * </pre>
     *
     * 간격이 좁아지지만 <b>순서와 상대적 거리는 그대로</b>라 기분 구분은 유지된다.
     */
    public EmotionPoint calibrate(EmotionPoint conceptual) {
        if (!enabled) return conceptual;
        return new EmotionPoint(
                map(conceptual.valence(), valenceMin, valenceMax),
                map(conceptual.arousal(), arousalMin, arousalMax));
    }

    private double map(double value, double min, double max) {
        if (max <= min) return value;   // 설정이 뒤집혔으면 보정하지 않는다
        return EmotionPoint.clamp(min + value * (max - min));
    }
}
