package com.example.musing_BE.recommendation.domain;

import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.dto.CandidateTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrackScorer — 점수가 낮을수록 목표 감정에 가깝다")
class TrackScorerTest {

    private TrackScorer scorer;

    /** 평온 + 바람 + 여름의 목표 좌표 */
    private final EmotionPoint TARGET = new EmotionPoint(0.70, 0.35);

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
        scorer = new TrackScorer(weights);
    }

    private CandidateTrack track(double valence, double energy, Double acousticness,
                                 String artist, String genre, Boolean korean) {
        return new CandidateTrack(1L, "곡", artist, "앨범", null, "preview",
                BigDecimal.valueOf(valence), BigDecimal.valueOf(energy),
                acousticness == null ? null : BigDecimal.valueOf(acousticness),
                genre, korean);
    }

    @Test
    @DisplayName("목표 좌표와 정확히 같으면 0점")
    void exactMatchScoresZero() {
        double score = scorer.score(track(0.70, 0.35, null, "A", "Pop", false),
                TARGET, Weather.WIND, null, null);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("멀수록 점수가 커진다")
    void fartherIsWorse() {
        double near = scorer.score(track(0.65, 0.30, null, "A", "Pop", false), TARGET, Weather.WIND, null, null);
        double far  = scorer.score(track(0.20, 0.85, null, "A", "Pop", false), TARGET, Weather.WIND, null, null);

        assertThat(near).isLessThan(far);
    }

    @Test
    @DisplayName("같은 좌표라면 한국 곡이 이긴다")
    void koreanWinsTie() {
        double korean  = scorer.score(track(0.70, 0.35, null, "아이유", "발라드", true), TARGET, Weather.WIND, null, null);
        double foreign = scorer.score(track(0.70, 0.35, null, "Adele", "Pop", false), TARGET, Weather.WIND, null, null);

        assertThat(korean).isLessThan(foreign);
    }

    @Test
    @DisplayName("한국 곡 가산점(0.03)은 감정 매칭을 뒤집지 못한다")
    void koreanBonusDoesNotOverrideEmotion() {
        // 좌표가 0.15 벗어난 한국 곡 vs 정확히 맞는 해외 곡
        double koreanOff  = scorer.score(track(0.55, 0.50, null, "아무개", "발라드", true), TARGET, Weather.WIND, null, null);
        double foreignFit = scorer.score(track(0.70, 0.35, null, "Adele", "Pop", false), TARGET, Weather.WIND, null, null);

        assertThat(koreanOff).isGreaterThan(foreignFit);   // 감정이 이긴다
    }

    @Test
    @DisplayName("seed와 같은 아티스트면 점수를 깎아 유리하게 한다")
    void sameArtistBonus() {
        CandidateTrack t = track(0.70, 0.35, null, "아이유", "발라드", false);

        double withSeed = scorer.score(t, TARGET, Weather.WIND, "아이유", null);
        double noSeed   = scorer.score(t, TARGET, Weather.WIND, null, null);

        assertThat(withSeed).isEqualTo(noSeed - 0.15);
    }

    @Test
    @DisplayName("아티스트 비교는 대소문자를 구분하지 않는다")
    void artistMatchIsCaseInsensitive() {
        CandidateTrack t = track(0.70, 0.35, null, "Adele", "Pop", false);

        assertThat(scorer.score(t, TARGET, Weather.WIND, "adele", null))
                .isEqualTo(scorer.score(t, TARGET, Weather.WIND, "Adele", null));
    }

    @Test
    @DisplayName("비 오는 날에는 어쿠스틱한 곡이 유리하다")
    void rainPrefersAcoustic() {
        CandidateTrack acoustic = track(0.70, 0.35, 0.9, "A", "Pop", false);
        CandidateTrack electric = track(0.70, 0.35, 0.1, "A", "Pop", false);

        // 맑은(바람) 날엔 어쿠스틱 보정이 없어 동점
        assertThat(scorer.score(acoustic, TARGET, Weather.WIND, null, null))
                .isEqualTo(scorer.score(electric, TARGET, Weather.WIND, null, null));

        // 비 오는 날엔 어쿠스틱이 이긴다
        assertThat(scorer.score(acoustic, TARGET, Weather.RAIN, null, null))
                .isLessThan(scorer.score(electric, TARGET, Weather.RAIN, null, null));
    }

    @Test
    @DisplayName("acousticness가 없으면 비 보정을 건너뛴다")
    void nullAcousticnessSkipsRainBonus() {
        CandidateTrack t = track(0.70, 0.35, null, "A", "Pop", false);

        assertThat(scorer.score(t, TARGET, Weather.RAIN, null, null)).isEqualTo(0.0);
    }
}
