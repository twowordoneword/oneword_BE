package com.example.musing_BE.stats.service;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.stats.dto.StatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService 통계 계산 테스트")
class StatsServiceTest {
    @Mock
    DiaryRepository diaryRepository;
    @Mock
    CurrentUserProvider currentUserProvider;
    @InjectMocks
    StatsService statsService;

    @Test
    @DisplayName("오늘 작성 + 연속 작성 + 최다 기분을 계산한다")
    void 오늘작성_연속작성_최다기분() {
        Long userId = 1L;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        given(currentUserProvider.getCurrentUserId()).willReturn(userId);
        given(diaryRepository.countByUserId(userId)).willReturn(10L);
        given(diaryRepository.countDiariesWithRole(userId, com.example.musing_BE.diary.domain.TrackRole.MY))
                .willReturn(8L);
        given(diaryRepository.existsByUserIdAndDiaryDate(userId, today)).willReturn(true);
        given(diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId))
                .willReturn(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)));
        given(diaryRepository.findMoodCountsByUserId(userId))
                .willReturn(List.of(new Object[]{Mood.CALM, 4L}, new Object[]{Mood.JOY, 3L}));

        StatsResponse res = statsService.getStats();

        assertThat(res.totalDiaries()).isEqualTo(10L);
        assertThat(res.totalMusic()).isEqualTo(8L);
        assertThat(res.todayWritten()).isTrue();
        assertThat(res.currentStreak()).isEqualTo(3L);
        assertThat(res.topMood()).isEqualTo("평온");
    }

    @Test
    @DisplayName("최근 기록이 오래됐으면 streak=0, topMood=null")
    void 오래된기록_스트릭0() {
        Long userId = 2L;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        given(currentUserProvider.getCurrentUserId()).willReturn(userId);
        given(diaryRepository.countByUserId(userId)).willReturn(2L);
        given(diaryRepository.countDiariesWithRole(userId, com.example.musing_BE.diary.domain.TrackRole.MY))
                .willReturn(1L);
        given(diaryRepository.existsByUserIdAndDiaryDate(userId, today)).willReturn(false);
        given(diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId))
                .willReturn(List.of(today.minusDays(3), today.minusDays(4)));
        given(diaryRepository.findMoodCountsByUserId(userId)).willReturn(List.of());

        StatsResponse res = statsService.getStats();

        assertThat(res.currentStreak()).isZero();
        assertThat(res.todayWritten()).isFalse();
        assertThat(res.topMood()).isNull();
    }
}
