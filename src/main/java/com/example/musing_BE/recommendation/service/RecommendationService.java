package com.example.musing_BE.recommendation.service;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.domain.DeterministicPicker;
import com.example.musing_BE.recommendation.domain.EmotionCalibrator;
import com.example.musing_BE.recommendation.domain.EmotionPoint;
import com.example.musing_BE.recommendation.domain.MoodWeatherSeasonMapper;
import com.example.musing_BE.recommendation.domain.ScoringWeights;
import com.example.musing_BE.recommendation.domain.TrackScorer;
import com.example.musing_BE.recommendation.dto.CandidateTrack;
import com.example.musing_BE.recommendation.dto.RecommendationResponse;
import com.example.musing_BE.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 오늘의 곡 추천 (RECOMMENDATION_STAGE2 §5).
 *
 * <pre>
 * 1. 목표 감정 좌표 계산        (기분 + 날씨 + 계절 → 실제 분포로 보정)
 * 2. tracks에서 후보 조회        감정값 있는 곡 — 없으면 재생 가능한 곡으로 폴백
 * 3. 최근 추천 곡 제외           14일 이내
 * 4. 점수 정렬                   거리 − 취향·한국곡 가산점
 * 5. 상위 30곡 중 고정 시드 선택  같은 조건이면 같은 곡
 * </pre>
 *
 * <p><b>외부 API를 부르지 않는다.</b> 후보 곡과 감정값은 배치가 미리 채워 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** 인증 도입 전 임시 사용자 (DiaryService·StatsService와 동일 관례). */
    private static final Long DEV_USER_ID = 1L;

    private final TrackRepository trackRepository;
    private final MoodWeatherSeasonMapper mapper;
    private final EmotionCalibrator calibrator;
    private final TrackScorer scorer;
    private final ScoringWeights weights;
    private final DeterministicPicker picker;

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Mood mood, Weather weather, LocalDate date,
                                            String seedName, String seedArtist) {
        // 1) 목표 감정 좌표 — 개념 좌표를 실제 곡 분포에 맞춰 보정한다
        EmotionPoint target = calibrator.calibrate(mapper.target(mood, weather, date));

        // 2) 후보 조회 — 감정값이 있으면 랭킹, 없으면(배치 전) 재생 가능한 곡에서 폴백
        List<CandidateTrack> candidates = trackRepository.findScorable();
        boolean rankable = !candidates.isEmpty();
        if (!rankable) {
            candidates = trackRepository.findPlayable();
            log.debug("감정값이 있는 곡이 없어 폴백 — 후보 {}곡", candidates.size());
        }
        if (candidates.isEmpty()) {
            return new RecommendationResponse(null);   // 배치 전이면 줄 곡이 없다
        }

        // 3) 최근 추천 곡 제외
        candidates = excludeRecent(candidates, date);

        // 4) 점수 정렬 → 상위 K
        List<CandidateTrack> pool = rankable
                ? topByScore(candidates, target, weather, seedArtist, seedName)
                : candidates;

        // 5) 같은 조건이면 같은 곡
        String seedKey = "%d|%s|%s|%s".formatted(DEV_USER_ID, date, mood.name(), weather.name());
        int index = picker.pickIndex(seedKey, pool.size());
        return new RecommendationResponse(pool.get(index).toTrackInfo());
    }

    /** 최근 N일 안에 추천된 곡을 뺀다. 다 빠져버리면 제외를 포기한다(곡은 줘야 하므로). */
    private List<CandidateTrack> excludeRecent(List<CandidateTrack> candidates, LocalDate date) {
        int days = weights.getExcludeRecentDays();
        if (days <= 0) return candidates;

        Set<Long> recent = new HashSet<>(
                trackRepository.findRecentlyRecommendedTrackIds(DEV_USER_ID, date.minusDays(days)));
        if (recent.isEmpty()) return candidates;

        List<CandidateTrack> filtered = candidates.stream()
                .filter(c -> !recent.contains(c.id()))
                .toList();
        return filtered.isEmpty() ? candidates : filtered;
    }

    /**
     * 점수가 낮은(목표에 가까운) 순으로 상위 K곡.
     * <p>비교자 안에서 점수를 계산하면 정렬 중 같은 곡을 여러 번 계산하게 되므로,
     * 곡마다 한 번만 계산해 담아 둔 뒤 정렬한다.
     */
    private List<CandidateTrack> topByScore(List<CandidateTrack> candidates, EmotionPoint target,
                                            Weather weather, String seedArtist, String seedName) {
        String seedGenre = findSeedGenre(seedName, seedArtist);
        return candidates.stream()
                .map(c -> new Scored(c, scorer.score(c, target, weather, seedArtist, seedGenre)))
                .sorted(Comparator.comparingDouble(Scored::score))
                .limit(Math.max(weights.getTopK(), 1))
                .map(Scored::track)
                .toList();
    }

    /** seed 곡이 우리 DB에 있으면 그 장르를 가산점 판정에 쓴다. */
    private String findSeedGenre(String seedName, String seedArtist) {
        if (seedName == null || seedName.isBlank() || seedArtist == null || seedArtist.isBlank()) {
            return null;
        }
        return trackRepository.findByNameAndArtist(seedName, seedArtist)
                .map(t -> t.getGenre())
                .orElse(null);
    }

    private record Scored(CandidateTrack track, double score) {}
}
