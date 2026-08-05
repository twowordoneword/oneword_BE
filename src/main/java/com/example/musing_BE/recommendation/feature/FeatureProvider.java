package com.example.musing_BE.recommendation.feature;

import com.example.musing_BE.recommendation.domain.EmotionPoint;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import java.util.Optional;

/**
 * 곡의 감정 특성(valence·arousal)을 제공하는 소스 추상화.
 * 구현체를 교체하면 추천 본체(Service)를 안 바꾸고 소스만 바꿀 수 있다 (RECOMMENDATION §2.1, ADR-010).
 * - 1단계: NoopFeatureProvider (값 없음 → 후보 랜덤 폴백)
 * - 2단계: FreqBlogFeatureProvider (배치가 채운 tracks 캐시 조회)
 */
public interface FeatureProvider {
    Optional<EmotionPoint> featuresFor(TrackInfoResponse track);
}
