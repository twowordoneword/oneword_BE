package com.example.musing_BE.recommendation.service;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.domain.EmotionPoint;
import com.example.musing_BE.recommendation.domain.MoodWeatherSeasonMapper;
import com.example.musing_BE.recommendation.dto.RecommendationResponse;
import com.example.musing_BE.recommendation.feature.FeatureProvider;
import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ItunesClient itunesClient;
    private final MoodWeatherSeasonMapper mapper;
    private final FeatureProvider featureProvider;
    private final Random random = new Random();

    private static final int CANDIDATE_LIMIT = 25;
    private static final int TOP_K = 5;

    /** 감정 좌표 매칭 추천. seedName/seedArtist는 선택. */
    public RecommendationResponse recommend(Mood mood, Weather weather, LocalDate date,
                                            String seedName, String seedArtist) {
        // 1) 목표 감정 좌표
        EmotionPoint target = mapper.target(mood, weather, date);
        // 2) 국내/해외 스토어프론트 + 검색어(seed 아티스트 우선, 없으면 기분 장르)
        String storefront = mapper.storefront(seedArtist);
        String term = (seedArtist != null && !seedArtist.isBlank())
                ? seedArtist : mapper.genreTerm(mood);

        // 3) 후보 수집
        List<TrackInfoResponse> candidates = itunesClient.searchSongs(term, CANDIDATE_LIMIT, storefront);
        if (candidates.isEmpty()) {
            return new RecommendationResponse(null);
        }

        // 4) 감정값 있으면 목표와의 거리로 랭킹, 없으면(1단계) 후보 중 랜덤 폴백
        List<Scored> scored = new ArrayList<>();
        for (TrackInfoResponse t : candidates) {
            featureProvider.featuresFor(t)
                    .ifPresent(pt -> scored.add(new Scored(t, pt.distanceTo(target))));
        }

        TrackInfoResponse chosen;
        if (!scored.isEmpty()) {
            scored.sort(Comparator.comparingDouble(Scored::distance));
            int k = Math.min(TOP_K, scored.size());
            chosen = scored.get(random.nextInt(k)).track(); // 상위 K 중 랜덤 (다양성)
        } else {
            chosen = candidates.get(random.nextInt(candidates.size())); // 1단계 폴백
        }
        return new RecommendationResponse(chosen);
    }

    private record Scored(TrackInfoResponse track, double distance) {}
}
