package com.example.musing_BE.track.dto;

/** 수집 배치 결과 요약. */
public record CollectResult(
        int storefronts,
        int artists,
        int fetched,
        int skipped,
        int inserted,
        int updated,
        long elapsedSeconds
) {}
