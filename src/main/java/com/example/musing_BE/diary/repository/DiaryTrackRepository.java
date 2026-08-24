package com.example.musing_BE.diary.repository;

import com.example.musing_BE.diary.entity.DiaryTrack;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DiaryTrackRepository extends JpaRepository<DiaryTrack, Long> {
    long countByDiaryUserId(Long userId);
    void deleteByDiaryUserId(Long userId);

    /**
     * 아카이브 첫 페이지 — 일기 날짜 내림차순.
     *
     * <p>일기·곡을 fetch join으로 함께 가져온다. 없으면 목록 크기만큼 추가 쿼리가 나간다(N+1).
     * 둘 다 @ManyToOne이라 컬렉션 페치가 아니므로 페이지네이션이 SQL에서 그대로 처리된다.
     */
    @Query("select dt from DiaryTrack dt "
         + "join fetch dt.diary d "
         + "join fetch dt.track "
         + "where d.user.id = :userId "
         + "order by d.diaryDate desc, dt.id desc")
    List<DiaryTrack> findArchiveFirstPage(@Param("userId") Long userId, Pageable pageable);

    /**
     * 아카이브 다음 페이지.
     *
     * <p>같은 날짜에 곡이 둘일 수 있어 날짜만으로는 경계를 못 정한다.
     * 날짜가 더 과거이거나, 같은 날짜면서 id가 더 작은 것을 가져온다.
     */
    @Query("select dt from DiaryTrack dt "
         + "join fetch dt.diary d "
         + "join fetch dt.track "
         + "where d.user.id = :userId "
         + "  and (d.diaryDate < :cursorDate "
         + "       or (d.diaryDate = :cursorDate and dt.id < :cursorId)) "
         + "order by d.diaryDate desc, dt.id desc")
    List<DiaryTrack> findArchiveAfter(@Param("userId") Long userId,
                                      @Param("cursorDate") LocalDate cursorDate,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);
}
