package com.example.musing_BE.diary.dto;

import com.example.musing_BE.track.domain.KoreanText;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.entity.Track;
import jakarta.validation.constraints.NotBlank;

/** 곡 정보(TrackInfo). API_SPEC 0.5의 공통 모델. */
public record TrackDto(
        Long id,
        @NotBlank String name,
        @NotBlank String artist,
        String album,
        String artworkUrl,
        String previewUrl
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
