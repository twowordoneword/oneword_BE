package com.example.musing_BE.diary.repository;

import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

    boolean existsByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);
    void deleteByUserId(Long userId);

    /** 월별 조회 — 곡까지 fetch join으로 한 번에 (N+1 방지). */
    @Query("select distinct d from Diary d "
         + "left join fetch d.diaryTracks dt "
         + "left join fetch dt.track "
         + "where d.user.id = :userId and d.diaryDate between :start and :end "
         + "order by d.diaryDate asc")
    List<Diary> findMonthlyWithTracks(@Param("userId") Long userId,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    // ----- 통계 -----

    /** 전체 일기 수 */
    long countByUserId(Long userId);

    /** 특정 기간(월) 일기 수 */
    long countByUserIdAndDiaryDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("select d.diaryDate from Diary d where d.user.id = :userId order by d.diaryDate desc")
    List<LocalDate> findDiaryDatesByUserIdOrderByDiaryDateDesc(@Param("userId") Long userId);

    @Query("select d.mood, count(d) from Diary d where d.user.id = :userId group by d.mood order by count(d) desc")
    List<Object[]> findMoodCountsByUserId(@Param("userId") Long userId);

    /** 특정 역할(MY/RECOMMENDED)의 곡이 연결된 일기 수. '음악 등록'은 MY 기준. */
    @Query("select count(distinct dt.diary.id) from DiaryTrack dt "
         + "where dt.diary.user.id = :userId and dt.role = :role")
    long countDiariesWithRole(@Param("userId") Long userId, @Param("role") TrackRole role);

    /** 작성 순번(seq): 이 일기가 몇 번째로 작성됐는지. id(auto increment) = 작성 순서. */
    @Query("select count(d) from Diary d where d.user.id = :userId and d.id <= :diaryId")
    long countSeqUpTo(@Param("userId") Long userId, @Param("diaryId") Long diaryId);
}
