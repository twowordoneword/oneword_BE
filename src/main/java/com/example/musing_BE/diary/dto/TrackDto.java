package com.example.musing_BE.diary.dto;

import com.example.musing_BE.track.domain.KoreanText;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.entity.Track;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 곡 정보(TrackInfo). API_SPEC 0.5의 공통 모델.
 *
 * <p>길이 제한은 {@code tracks} 테이블 컬럼과 같은 값이다. 검증이 없으면 긴 값이 DB까지 내려가
 * "Data too long" 제약 위반으로 터지는데, 그건 원인을 알기 어려운 409/500으로 나간다.
 * 사용자 입력 문제는 DB에 닿기 전에 400으로 돌려주는 편이 낫다.
 */
public record TrackDto(
        Long id,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String artist,
        @Size(max = 255) String album,
        @Size(max = 500) String artworkUrl,
        @Size(max = 500) String previewUrl
) {
    public static TrackDto from(Track t) {
        if (t == null) return null;
        return new TrackDto(t.getId(), t.getName(), t.getArtist(), t.getAlbum(),
                t.getArtworkUrl(), t.getPreviewUrl());
    }

    /** 일기에서 유입된 곡 → origin=USER. 한국 곡 여부는 아티스트명으로 판정(§5.3). */
    public Track toNewEntity() {
        return Track.builder()
                .name(name).artist(artist).album(album)
                .artworkUrl(artworkUrl).previewUrl(previewUrl)
                .origin(TrackOrigin.USER)
                .isKorean(KoreanText.containsHangul(artist))
                .build();
    }
}
