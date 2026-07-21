package com.example.musing_BE.diary.dto;

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

    public Track toNewEntity() {
        return Track.builder()
                .name(name).artist(artist).album(album)
                .artworkUrl(artworkUrl).previewUrl(previewUrl)
                .build();
    }
}
