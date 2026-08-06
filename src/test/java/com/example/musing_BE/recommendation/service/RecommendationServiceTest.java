package com.example.musing_BE.recommendation.service;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.domain.DeterministicPicker;
import com.example.musing_BE.recommendation.domain.EmotionCalibrator;
import com.example.musing_BE.recommendation.domain.MoodWeatherSeasonMapper;
import com.example.musing_BE.recommendation.domain.ScoringWeights;
import com.example.musing_BE.recommendation.domain.TrackScorer;
import com.example.musing_BE.recommendation.dto.CandidateTrack;
import com.example.musing_BE.recommendation.dto.RecommendationResponse;
import com.example.musing_BE.track.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecommendationService — DB 후보에서 곡 한 곡 고르기")
class RecommendationServiceTest {

    @Mock TrackRepository trackRepository;

    RecommendationService service;

    private final LocalDate DATE = LocalDate.of(2026, 8, 5);

    @BeforeEach
    void setUp() {
        ScoringWeights weights = new ScoringWeights();
        ReflectionTestUtils.setField(weights, "weightValence", 1.0);
        ReflectionTestUtils.setField(weights, "weightArousal", 1.0);
        ReflectionTestUtils.setField(weights, "weightAcousticRain", 0.5);
        ReflectionTestUtils.setField(weights, "bonusSameArtist", 0.15);
        ReflectionTestUtils.setField(weights, "bonusSameGenre", 0.05);
        ReflectionTestUtils.setField(weights, "bonusKorean", 0.03);
        ReflectionTestUtils.setField(weights, "topK", 30);
        ReflectionTestUtils.setField(weights, "excludeRecentDays", 14);

        service = new RecommendationService(
                trackRepository,
                new MoodWeatherSeasonMapper(),
                new EmotionCalibrator(),
                new TrackScorer(weights),
                weights,
                new DeterministicPicker());
    }

    private CandidateTrack scored(long id, String name, double valence, double energy) {
        return new CandidateTrack(id, name, "가수" + id, "앨범", null, "preview",
                BigDecimal.valueOf(valence), BigDecimal.valueOf(energy),
                null, "Pop", false);
    }

    private CandidateTrack unscored(long id, String name) {
        return new CandidateTrack(id, name, "가수" + id, "앨범", null, "preview",
                null, null, null, "Pop", false);
    }

    @Test
    @DisplayName("후보가 아예 없으면 track = null")
    void emptyPoolReturnsNull() {
        given(trackRepository.findScorable()).willReturn(List.of());
        given(trackRepository.findPlayable()).willReturn(List.of());

        RecommendationResponse res = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null);

        assertThat(res.track()).isNull();
    }

    @Test
    @DisplayName("감정값 있는 곡이 없으면 재생 가능한 곡으로 폴백한다")
    void fallsBackToPlayable() {
        given(trackRepository.findScorable()).willReturn(List.of());
        given(trackRepository.findPlayable()).willReturn(List.of(unscored(1L, "폴백곡")));
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of());

        RecommendationResponse res = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null);

        assertThat(res.track()).isNotNull();
        assertThat(res.track().name()).isEqualTo("폴백곡");
    }

    @Test
    @DisplayName("같은 조건으로 여러 번 요청해도 같은 곡")
    void sameConditionsSameTrack() {
        given(trackRepository.findScorable()).willReturn(List.of(
                scored(1L, "곡A", 0.70, 0.35),
                scored(2L, "곡B", 0.68, 0.33),
                scored(3L, "곡C", 0.72, 0.37)));
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of());

        String first = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null).track().name();

        for (int i = 0; i < 20; i++) {
            assertThat(service.recommend(Mood.CALM, Weather.WIND, DATE, null, null).track().name())
                    .isEqualTo(first);
        }
    }

    @Test
    @DisplayName("최근 추천된 곡은 후보에서 빠진다")
    void excludesRecentlyRecommended() {
        given(trackRepository.findScorable()).willReturn(List.of(
                scored(1L, "최근에 나온 곡", 0.70, 0.35),
                scored(2L, "새 곡", 0.70, 0.35)));
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of(1L));

        RecommendationResponse res = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null);

        assertThat(res.track().name()).isEqualTo("새 곡");
    }

    @Test
    @DisplayName("전부 최근에 추천된 곡이면 제외를 포기하고 그중에서 고른다")
    void keepsGoingWhenEverythingExcluded() {
        given(trackRepository.findScorable()).willReturn(List.of(scored(1L, "유일한 곡", 0.70, 0.35)));
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of(1L));

        RecommendationResponse res = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null);

        assertThat(res.track()).isNotNull();   // 곡은 반드시 준다
        assertThat(res.track().name()).isEqualTo("유일한 곡");
    }

    @Test
    @DisplayName("목표 좌표에서 아주 먼 곡은 상위 K 밖으로 밀려 뽑히지 않는다")
    void farTrackIsNotPicked() {
        ScoringWeights weights = (ScoringWeights) ReflectionTestUtils.getField(service, "weights");
        ReflectionTestUtils.setField(weights, "topK", 1);   // 최상위 1곡만 후보로

        given(trackRepository.findScorable()).willReturn(List.of(
                scored(1L, "딱 맞는 곡", 0.70, 0.35),
                scored(2L, "정반대 곡", 0.05, 0.95)));
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of());

        RecommendationResponse res = service.recommend(Mood.CALM, Weather.WIND, DATE, null, null);

        assertThat(res.track().name()).isEqualTo("딱 맞는 곡");
    }

    @Test
    @DisplayName("기분에 따라 감정적으로 맞는 곡이 뽑힌다")
    void moodAffectsResult() {
        // 상위 1곡만 후보로 좁혀야 '점수 1등이 뽑히는지'를 단정할 수 있다.
        // (K가 크면 고정 시드가 상위권 중 어느 것이든 고를 수 있어 결과가 하나로 정해지지 않는다)
        ScoringWeights weights = (ScoringWeights) ReflectionTestUtils.getField(service, "weights");
        ReflectionTestUtils.setField(weights, "topK", 1);

        List<CandidateTrack> pool = List.of(
                scored(1L, "밝고 신나는 곡", 0.90, 0.80),
                scored(2L, "어둡고 잔잔한 곡", 0.15, 0.20));
        given(trackRepository.findScorable()).willReturn(pool);
        given(trackRepository.findRecentlyRecommendedTrackIds(anyLong(), any())).willReturn(List.of());

        String joy = service.recommend(Mood.JOY, Weather.SUNNY, DATE, null, null).track().name();
        String sad = service.recommend(Mood.SAD, Weather.RAIN, DATE, null, null).track().name();

        assertThat(joy).isEqualTo("밝고 신나는 곡");
        assertThat(sad).isEqualTo("어둡고 잔잔한 곡");
    }
}
