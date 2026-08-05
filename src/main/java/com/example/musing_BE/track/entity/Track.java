package com.example.musing_BE.track.entity;

import com.example.musing_BE.track.domain.TrackOrigin;
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

    // 수집 정보 (RECOMMENDATION_STAGE2 §3.0, §5.3)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TrackOrigin origin;

    @Column(length = 50)
    private String genre;

    /** 아티스트명에 한글 포함 → 한국 곡 가산점(§5.3)에 사용. */
    @Column(name = "is_korean")
    private Boolean isKorean;

    // 감정 특성 캐시 (감정값 배치가 채움 — 미조회 시 null)
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

    /**
     * 수집 배치가 기존 곡을 다시 만났을 때 비어 있는 칸만 채운다.
     * origin은 최초 유입 경로를 보존한다(사용자가 먼저 붙인 곡을 CHART로 덮지 않음).
     */
    public void fillCollected(String genre, Boolean isKorean, TrackOrigin origin) {
        if (this.genre == null && genre != null) this.genre = genre;
        if (this.isKorean == null && isKorean != null) this.isKorean = isKorean;
        if (this.origin == null && origin != null) this.origin = origin;
    }

    /** 감정값 배치가 특성을 기록한다. 곡당 1회. */
    public void updateFeatures(BigDecimal valence, BigDecimal energy,
                               BigDecimal acousticness, BigDecimal tempo, String source) {
        this.valence = valence;
        this.energy = energy;
        this.acousticness = acousticness;
        this.tempo = tempo;
        this.featuresSource = source;
        this.featuresFetchedAt = LocalDateTime.now();
    }

    @Builder
    private Track(String name, String artist, String isrc, String album, String artworkUrl,
                  String previewUrl, TrackOrigin origin, String genre, Boolean isKorean) {
        this.name = name;
        this.artist = artist;
        this.isrc = isrc;
        this.album = album;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.origin = origin;
        this.genre = genre;
        this.isKorean = isKorean;
    }
}
