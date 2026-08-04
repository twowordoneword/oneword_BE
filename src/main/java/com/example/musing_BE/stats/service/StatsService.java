package com.example.musing_BE.stats.service;

import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final DiaryRepository diaryRepository;

    // TODO(auth): 인증 슬라이스에서 실제 사용자로 대체
    private static final Long DEV_USER_ID = 1L;

    /** month(YYYY-MM)가 있으면 그 달 일기 수도 함께 반환. */
    public StatsResponse getStats(String month) {
        long totalCount = diaryRepository.countByUserId(DEV_USER_ID);
        long musicCount = diaryRepository.countDiariesWithRole(DEV_USER_ID, TrackRole.MY);

        Long monthlyCount = null;
        if (month != null && !month.isBlank()) {
            YearMonth ym = YearMonth.parse(month); // 형식 오류 시 IllegalArgumentException -> 400
            monthlyCount = diaryRepository.countByUserIdAndDiaryDateBetween(
                    DEV_USER_ID, ym.atDay(1), ym.atEndOfMonth());
        }
        return new StatsResponse(totalCount, monthlyCount, musicCount);
    }
}
