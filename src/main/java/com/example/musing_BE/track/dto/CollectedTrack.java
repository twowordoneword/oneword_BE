package com.example.musing_BE.track.dto;

/**
 * 수집 배치가 다루는 곡 (RECOMMENDATION_STAGE2 §3).
 * TrackInfoResponse(응답용)와 달리 수집에 필요한 식별자·장르를 함께 갖는다.
 */
public record CollectedTrack(
        Long trackId,
        Long artistId,
        String name,
        String artist,
        String album,
        String artworkUrl,
        String previewUrl,
        String genre
) {}
