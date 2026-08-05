package com.example.musing_BE.track.repository;

import com.example.musing_BE.recommendation.dto.CandidateTrack;
import com.example.musing_BE.track.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByIsrc(String isrc);

    Optional<Track> findByNameAndArtist(String name, String artist);

    /**
     * 감정 거리 랭킹이 가능한 후보 — valence·energy가 채워진 곡.
     * 감정값 배치(§4)가 돌기 전에는 비어 있고, 그때는 {@link #findPlayable()}로 폴백한다.
     */
    @Query("""
            select new com.example.musing_BE.recommendation.dto.CandidateTrack(
                t.id, t.name, t.artist, t.album, t.artworkUrl, t.previewUrl,
                t.valence, t.energy, t.acousticness, t.genre, t.isKorean)
            from Track t
            where t.valence is not null and t.energy is not null
              and t.previewUrl is not null
            """)
    List<CandidateTrack> findScorable();

    /** 폴백용 — 감정값은 없지만 재생 가능한 곡. */
    @Query("""
            select new com.example.musing_BE.recommendation.dto.CandidateTrack(
                t.id, t.name, t.artist, t.album, t.artworkUrl, t.previewUrl,
                t.valence, t.energy, t.acousticness, t.genre, t.isKorean)
            from Track t
            where t.previewUrl is not null
            """)
    List<CandidateTrack> findPlayable();

    /**
     * 감정값 배치 대상 (§4) — <b>감정값이 없고 미리듣기가 있는 곡</b>.
     * 차트·설문·사용자 곡을 구분하지 않는다. 무료 할당량 보호를 위해 건수를 제한해 가져온다.
     */
    @Query("""
            select new com.example.musing_BE.recommendation.dto.CandidateTrack(
                t.id, t.name, t.artist, t.album, t.artworkUrl, t.previewUrl,
                t.valence, t.energy, t.acousticness, t.genre, t.isKorean)
            from Track t
            where t.valence is null and t.previewUrl is not null
            order by t.id asc
            """)
    List<CandidateTrack> findFeaturePending(Limit limit);

    /**
     * 최근 N일 안에 이 사용자에게 추천된 곡 ID (§5.2).
     * 판정 기준은 일기에 저장된 추천곡(role=RECOMMENDED)이다.
     */
    @Query("""
            select dt.track.id from DiaryTrack dt
            where dt.diary.user.id = :userId
              and dt.role = com.example.musing_BE.diary.domain.TrackRole.RECOMMENDED
              and dt.diary.diaryDate >= :since
            """)
    List<Long> findRecentlyRecommendedTrackIds(@Param("userId") Long userId,
                                               @Param("since") LocalDate since);
}
