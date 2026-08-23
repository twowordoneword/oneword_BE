package com.example.musing_BE.recommendation.domain;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("MoodWeatherSeasonMapper — 기분 + 날씨 + 계절 = 목표 좌표")
class MoodWeatherSeasonMapperTest {

    private final MoodWeatherSeasonMapper mapper = new MoodWeatherSeasonMapper();

    @Test
    @DisplayName("평온 + 바람 + 여름 = (0.70, 0.35)")
    void calmWindSummer() {
        EmotionPoint p = mapper.target(Mood.CALM, Weather.WIND, LocalDate.of(2026, 8, 5));

        assertThat(p.valence()).isCloseTo(0.70, within(1e-9));  // 0.70 + 0.00 + 0.00
        assertThat(p.arousal()).isCloseTo(0.35, within(1e-9));  // 0.25 + 0.05 + 0.05
    }

    @Test
    @DisplayName("같은 기분이라도 날씨가 다르면 좌표가 달라진다")
    void weatherMovesPoint() {
        LocalDate summer = LocalDate.of(2026, 8, 5);

        EmotionPoint sunny = mapper.target(Mood.CALM, Weather.SUNNY, summer);
        EmotionPoint rain  = mapper.target(Mood.CALM, Weather.RAIN, summer);

        assertThat(sunny.valence()).isGreaterThan(rain.valence());   // 맑음이 더 밝다
        assertThat(sunny.arousal()).isGreaterThan(rain.arousal());
    }

    @Test
    @DisplayName("같은 기분·날씨라도 계절이 다르면 좌표가 달라진다")
    void seasonMovesPoint() {
        EmotionPoint spring = mapper.target(Mood.NORMAL, Weather.SUNNY, LocalDate.of(2026, 4, 1));
        EmotionPoint autumn = mapper.target(Mood.NORMAL, Weather.SUNNY, LocalDate.of(2026, 10, 1));

        assertThat(spring.valence()).isGreaterThan(autumn.valence());  // 봄 +0.05 / 가을 -0.05
    }

    @Test
    @DisplayName("보정을 더해도 0~1을 벗어나지 않는다 (클램프)")
    void clampsToRange() {
        // 기쁨(0.90, 0.75) + 맑음(+0.10, +0.05) + 여름(0, +0.05) → valence 1.00, arousal 0.85
        EmotionPoint high = mapper.target(Mood.JOY, Weather.SUNNY, LocalDate.of(2026, 7, 1));
        assertThat(high.valence()).isBetween(0.0, 1.0);
        assertThat(high.arousal()).isBetween(0.0, 1.0);

        // 슬픔(0.15, 0.25) + 비(-0.15, -0.10) + 겨울(0, -0.05) → valence 0.00, arousal 0.10
        EmotionPoint low = mapper.target(Mood.SAD, Weather.RAIN, LocalDate.of(2026, 1, 15));
        assertThat(low.valence()).isBetween(0.0, 1.0);
        assertThat(low.arousal()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("모든 기분·날씨·계절 조합에서 좌표가 유효 범위를 지킨다")
    void allCombinationsStayInRange() {
        int[] months = {1, 4, 7, 10};
        for (Mood mood : Mood.values()) {
            for (Weather weather : Weather.values()) {
                for (int month : months) {
                    EmotionPoint p = mapper.target(mood, weather, LocalDate.of(2026, month, 15));
                    assertThat(p.valence()).isBetween(0.0, 1.0);
                    assertThat(p.arousal()).isBetween(0.0, 1.0);
                }
            }
        }
    }

    @Test
    @DisplayName("감정 지도에서 기쁨과 슬픔은 멀고, 슬픔과 우울은 가깝다")
    void moodDistances() {
        LocalDate d = LocalDate.of(2026, 5, 1);
        EmotionPoint joy   = mapper.target(Mood.JOY, Weather.SUNNY, d);
        EmotionPoint sad   = mapper.target(Mood.SAD, Weather.SUNNY, d);
        EmotionPoint gloom = mapper.target(Mood.GLOOM, Weather.SUNNY, d);

        assertThat(joy.distanceTo(sad)).isGreaterThan(0.5);
        // ⚠️ 슬픔·우울이 0.1 미만으로 붙어 있어 사실상 같은 추천이 나온다(문서 §11의 튜닝 과제)
        assertThat(sad.distanceTo(gloom)).isLessThan(0.1);
    }
}
