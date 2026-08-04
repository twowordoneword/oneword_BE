package com.example.musing_BE.diary.dto;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.diary.entity.Diary;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일기 상세 응답. API_SPEC 2.2. */
public record DiaryDetailResponse(
        Long id,
        long seq,          // 작성 순번 (작성순 1부터) — No.XX 표시용
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
    public static DiaryDetailResponse from(Diary d, long seq) {
        return new DiaryDetailResponse(
                d.getId(), seq, d.getDiaryDate(), d.getTitle(), d.getBody(),
                d.getMood(), d.getWeather(),
                TrackDto.from(d.getMyTrack()),
                TrackDto.from(d.getTodayTrack()),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
