package com.example.musing_BE.diary.dto;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.diary.entity.Diary;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일기 상세 응답. API_SPEC 2.2. */
public record DiaryDetailResponse(
        Long id,
        LocalDate date,
        String title,
        String body,
        Mood mood,
        Weather weather,
        TrackDto myTrack,
        TrackDto todayTrack,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DiaryDetailResponse from(Diary d) {
        return new DiaryDetailResponse(
                d.getId(), d.getDiaryDate(), d.getTitle(), d.getBody(),
                d.getMood(), d.getWeather(),
                TrackDto.from(d.getMyTrack()),
                TrackDto.from(d.getTodayTrack()),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
