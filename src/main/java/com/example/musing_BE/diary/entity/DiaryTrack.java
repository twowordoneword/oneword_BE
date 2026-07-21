package com.example.musing_BE.diary.entity;

import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.track.entity.Track;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "diary_tracks",
        uniqueConstraints = @UniqueConstraint(name = "uq_diary_role", columnNames = {"diary_id", "role"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TrackRole role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    private DiaryTrack(Diary diary, Track track, TrackRole role) {
        this.diary = diary;
        this.track = track;
        this.role = role;
    }

    static DiaryTrack of(Diary diary, Track track, TrackRole role) {
        return new DiaryTrack(diary, track, role);
    }
}
