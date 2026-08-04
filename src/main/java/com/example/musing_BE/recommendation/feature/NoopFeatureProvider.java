package com.example.musing_BE.recommendation.feature;

import com.example.musing_BE.recommendation.domain.EmotionPoint;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * 1단계용 빈 껍데기. 아직 감정 특성 소스 미연동이라 항상 값 없음(empty).
 * → RecommendationService가 거리 랭킹을 건너뛰고 후보 중 랜덤으로 폴백한다.
 * 2단계에서 ReccoBeats/Essentia 구현체로 교체.
 */
@Component
public class NoopFeatureProvider implements FeatureProvider {
    @Override
    public Optional<EmotionPoint> featuresFor(TrackInfoResponse track) {
        return Optional.empty();
    }
}
