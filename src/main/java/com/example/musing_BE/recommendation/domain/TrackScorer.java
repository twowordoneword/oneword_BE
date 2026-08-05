package com.example.musing_BE.recommendation.domain;

import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.dto.CandidateTrack;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 곡 점수 계산 (RECOMMENDATION_STAGE2 §5.1).
 *
 * <pre>
 * score = wV·(곡.valence − 목표.valence)²
 *       + wA·(곡.arousal − 목표.arousal)²
 *       + (비일 때 wAc·(1 − 곡.acousticness)²)
 *       − 취향 가산점 − 한국 곡 가산점
 * </pre>
 *
 * <b>점수가 낮을수록 좋다</b>(목표 좌표와의 거리이므로). 가산점은 그래서 빼준다.
 */
@Component
@RequiredArgsConstructor
public class TrackScorer {

    private final ScoringWeights weights;

    /**
     * @param seedArtist 사용자가 고른 '나의 음악'의 아티스트 (없으면 null)
     * @param seedGenre  그 곡의 장르 (없으면 null)
     */
    public double score(CandidateTrack track, EmotionPoint target, Weather weather,
                        String seedArtist, String seedGenre) {
        double dv = toDouble(track.valence()) - target.valence();
        double da = toDouble(track.energy()) - target.arousal();

        double score = weights.getWeightValence() * dv * dv
                     + weights.getWeightArousal() * da * da;

        // 비 오는 날엔 어쿠스틱한 곡을 당긴다 (acousticness가 1에 가까울수록 벌점이 준다)
        if (weather == Weather.RAIN && track.acousticness() != null) {
            double gap = 1.0 - toDouble(track.acousticness());
            score += weights.getWeightAcousticRain() * gap * gap;
        }

        // 취향 가산점 — 후보를 좁히지 않고 점수만 깎는다(같은 가수만 반복되는 것 방지)
        if (matches(seedArtist, track.artist())) {
            score -= weights.getBonusSameArtist();
        }
        if (matches(seedGenre, track.genre())) {
            score -= weights.getBonusSameGenre();
        }
        if (Boolean.TRUE.equals(track.isKorean())) {
            score -= weights.getBonusKorean();
        }
        return score;
    }

    private boolean matches(String a, String b) {
        return a != null && !a.isBlank() && b != null && a.equalsIgnoreCase(b.trim());
    }

    private double toDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
