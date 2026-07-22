package com.example.musing_BE.track.dto;

/** 외부(iTunes 등)에서 온 곡 정보. API_SPEC의 TrackInfo. */
public record TrackInfoResponse(
        String name,
        String artist,
        String album,
        String artworkUrl,
        String previewUrl
) {}
