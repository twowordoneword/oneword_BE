package com.example.musing_BE.track.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracks",
        uniqueConstraints = @UniqueConstraint(name = "uq_tracks_name_artist", columnNames = {"name", "artist"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String artist;

    @Column(length = 15)
    private String isrc;

    private String album;

    @Column(name = "artwork_url", length = 500)
    private String artworkUrl;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    // 감정 특성 캐시 (추천 슬라이스에서 채움 — 지금은 nullable)
    private BigDecimal valence;
    private BigDecimal energy;
    private BigDecimal acousticness;
    private BigDecimal tempo;

    @Column(name = "features_source", length = 20)
    private String featuresSource;

    @Column(name = "features_fetched_at")
    private LocalDateTime featuresFetchedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    /** 기존 곡 재사용 시 메타 갱신(전달된 값이 있을 때만). */
    public void updateMeta(String album, String artworkUrl, String previewUrl) {
        if (album != null) this.album = album;
        if (artworkUrl != null) this.artworkUrl = artworkUrl;
        if (previewUrl != null) this.previewUrl = previewUrl;
    }

    @Builder
    private Track(String name, String artist, String isrc, String album, String artworkUrl, String previewUrl) {
        this.name = name;
        this.artist = artist;
        this.isrc = isrc;
        this.album = album;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
    }
}
