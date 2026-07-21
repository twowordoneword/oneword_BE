package com.example.musing_BE.recommendation.domain;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/**
 * 기분·날씨·계절 → 목표 감정 좌표 + iTunes 검색어 매핑 (RECOMMENDATION.md §3).
 * 값은 시작값이며 실제 청취 튜닝 대상.
 */
@Component
public class MoodWeatherSeasonMapper {

    /** 최종 목표 좌표 = 기분 + 날씨Δ + 계절Δ, [0,1] 클램프. */
    public EmotionPoint target(Mood mood, Weather weather, LocalDate date) {
        double[] base = moodBase(mood);
        double[] w = weatherDelta(weather);
        double[] s = seasonDelta(date);
        double valence = EmotionPoint.clamp(base[0] + w[0] + s[0]);
        double arousal = EmotionPoint.clamp(base[1] + w[1] + s[1]);
        return new EmotionPoint(valence, arousal);
    }

    private double[] moodBase(Mood mood) {
        return switch (mood) {
            case JOY     -> new double[]{0.90, 0.75};
            case CALM    -> new double[]{0.70, 0.25};
            case NORMAL  -> new double[]{0.50, 0.50};
            case UNKNOWN -> new double[]{0.50, 0.50};
            case STUFFY  -> new double[]{0.30, 0.60};
            case ANGRY   -> new double[]{0.20, 0.85};
            case GLOOM   -> new double[]{0.20, 0.30};
            case SAD     -> new double[]{0.15, 0.25};
        };
    }

    private double[] weatherDelta(Weather weather) {
        return switch (weather) {
            case SUNNY  -> new double[]{ 0.10,  0.05};
            case WIND   -> new double[]{ 0.00,  0.05};
            case CLOUDY -> new double[]{-0.10, -0.05};
            case SNOW   -> new double[]{-0.05, -0.10};
            case RAIN   -> new double[]{-0.15, -0.10};
        };
    }

    private double[] seasonDelta(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 3 && m <= 5)  return new double[]{ 0.05, 0.00}; // 봄
        if (m >= 6 && m <= 8)  return new double[]{ 0.00, 0.05}; // 여름
        if (m >= 9 && m <= 11) return new double[]{-0.05, 0.00}; // 가을
        return new double[]{0.00, -0.05};                        // 겨울
    }

    /** seed 아티스트가 없을 때 iTunes 검색어(장르 키워드). */
    public String genreTerm(Mood mood) {
        return switch (mood) {
            case JOY -> "K-Pop";
            case CALM -> "acoustic";
            case NORMAL, UNKNOWN -> "pop";
            case STUFFY -> "alternative";
            case ANGRY -> "rock";
            case GLOOM, SAD -> "ballad";
        };
    }

    /** seed 아티스트에 한글이 있으면 국내(KR), 없으면 해외(US). null이면 KR 기본. */
    public String storefront(String seedArtist) {
        if (seedArtist == null || seedArtist.isBlank()) return "KR";
        return seedArtist.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3) ? "KR" : "US";
    }
}
