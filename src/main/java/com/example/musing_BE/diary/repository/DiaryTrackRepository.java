package com.example.musing_BE.diary.repository;

import com.example.musing_BE.diary.entity.DiaryTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryTrackRepository extends JpaRepository<DiaryTrack, Long> {
    long countByDiaryUserId(Long userId);
    void deleteByDiaryUserId(Long userId);
}
