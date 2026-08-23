package com.example.musing_BE.track.dto;

/** 감정값 배치 결과. */
public record FeatureFillResult(
        int targeted,
        int filled,
        int notFound,
        boolean stoppedByQuota,
        long elapsedSeconds
) {}
