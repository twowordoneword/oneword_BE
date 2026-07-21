package com.example.musing_BE.diary.repository;

import com.example.musing_BE.diary.entity.DiaryTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryTrackRepository extends JpaRepository<DiaryTrack, Long> {
}
