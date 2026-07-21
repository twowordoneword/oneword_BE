package com.example.musing_BE.diary.dto;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 일기 작성/수정 요청 (POST/PUT 공용). */
public record DiaryUpsertRequest(
        @NotNull LocalDate date,
        String title,
        String body,
        @NotNull Mood mood,
        @NotNull Weather weather,
        @Valid TrackDto myTrack,
        @Valid TrackDto todayTrack
) {}
