package com.example.musing_BE.diary.repository;

import com.example.musing_BE.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

    boolean existsByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

    List<Diary> findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(
            Long userId, LocalDate start, LocalDate end);
}
