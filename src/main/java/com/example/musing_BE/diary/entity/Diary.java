package com.example.musing_BE.diary.entity;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.track.entity.Track;
import com.example.musing_BE.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_diaries_user_date", columnNames = {"user_id", "diary_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 10)
    private Mood mood;

    @Column(nullable = false, length = 10)
    private Weather weather;

    @OneToMany(mappedBy = "diary", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiaryTrack> diaryTracks = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { this.createdAt = this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    private Diary(User user, LocalDate diaryDate, String title, String body, Mood mood, Weather weather) {
        this.user = user;
        this.diaryDate = diaryDate;
        this.title = title;
        this.body = body;
        this.mood = mood;
        this.weather = weather;
    }

    public static Diary create(User user, LocalDate diaryDate, String title, String body, Mood mood, Weather weather) {
        return new Diary(user, diaryDate, title, body, mood, weather);
    }

    /** 전체 교체 수정(PUT). 곡 연결은 서비스에서 재설정. */
    public void update(String title, String body, Mood mood, Weather weather) {
        this.title = title;
        this.body = body;
        this.mood = mood;
        this.weather = weather;
    }

    public void attachTrack(Track track, TrackRole role) {
        this.diaryTracks.add(DiaryTrack.of(this, track, role));
    }

    public void clearTracks() {
        this.diaryTracks.clear();
    }

    public Track findTrackByRole(TrackRole role) {
        return diaryTracks.stream()
                .filter(dt -> dt.getRole() == role)
                .map(DiaryTrack::getTrack)
                .findFirst()
                .orElse(null);
    }

    public Track getMyTrack() { return findTrackByRole(TrackRole.MY); }
    public Track getTodayTrack() { return findTrackByRole(TrackRole.RECOMMENDED); }
    public boolean hasMusic() { return !diaryTracks.isEmpty(); }
}
