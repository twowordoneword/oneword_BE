package com.example.musing_BE.stats.dto;

public record StatsResponse(
        long totalDiaries,
        long totalMusic,
        long currentStreak,
        boolean todayWritten,
        String topMood
) {}
