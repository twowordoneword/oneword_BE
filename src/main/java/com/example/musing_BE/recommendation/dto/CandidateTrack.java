package com.example.musing_BE.recommendation.dto;

import com.example.musing_BE.track.dto.TrackInfoResponse;
import java.math.BigDecimal;

/**
 * 추천 후보 (DB 조회용 프로젝션).
 * <p>엔티티(Track) 전체를 로딩하면 필요 없는 컬럼·영속성 컨텍스트 관리 비용이 붙으므로,
 * 점수 계산에 필요한 값만 뽑아 온다.
 */
public record CandidateTrack(
        Long id,
        String name,
        String artist,
        String album,
        String artworkUrl,
        String previewUrl,
        BigDecimal valence,
        BigDecimal energy,
        BigDecimal acousticness,
        String genre,
        Boolean isKorean
) {
    /** 감정 거리 계산이 가능한 곡인지. */
    public boolean hasFeatures() {
        return valence != null && energy != null;
    }

    public TrackInfoResponse toTrackInfo() {
        return new TrackInfoResponse(name, artist, album, artworkUrl, previewUrl);
    }
}
