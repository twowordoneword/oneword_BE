package com.example.musing_BE.stats.service;

import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final DiaryRepository diaryRepository;
    private final CurrentUserProvider currentUserProvider;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public StatsResponse getStats() {
        Long userId = currentUserProvider.getCurrentUserId();
        long totalDiaries = diaryRepository.countByUserId(userId);
        long totalMusic = diaryRepository.countDiariesWithRole(userId, TrackRole.MY);

        LocalDate today = LocalDate.now(KST);
        boolean todayWritten = diaryRepository.existsByUserIdAndDiaryDate(userId, today);
        long currentStreak = calculateCurrentStreak(today, diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId));
        String topMood = resolveTopMood(userId);

        return new StatsResponse(totalDiaries, totalMusic, currentStreak, todayWritten, topMood);
    }

    private long calculateCurrentStreak(LocalDate today, List<LocalDate> datesDesc) {
        if (datesDesc.isEmpty()) {
            return 0;
        }
        LocalDate latest = datesDesc.getFirst();
        if (latest.isBefore(today.minusDays(1))) {
            return 0;
        }

        long streak = 0;
        LocalDate cursor = latest;
        for (LocalDate date : datesDesc) {
            if (!date.equals(cursor)) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private String resolveTopMood(Long userId) {
        List<Object[]> moodCounts = diaryRepository.findMoodCountsByUserId(userId);
        if (moodCounts.isEmpty()) {
            return null;
        }
        Object mood = moodCounts.getFirst()[0];
        if (mood instanceof Mood m) {
            return m.getCode();
        }
        return mood == null ? null : mood.toString();
    }
}
