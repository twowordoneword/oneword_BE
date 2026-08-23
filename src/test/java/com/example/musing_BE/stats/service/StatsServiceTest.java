package com.example.musing_BE.stats.service;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.stats.dto.StatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService 통계 계산 테스트")
class StatsServiceTest {
    @Mock
    DiaryRepository diaryRepository;
    @Mock
    CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("오늘 작성 + 연속 작성 + 최다 기분을 계산한다")
    void 오늘작성_연속작성_최다기분() {
        Long userId = 1L;
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDate today = LocalDate.now(clock);
        StatsService statsService = new StatsService(diaryRepository, currentUserProvider, clock);
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDate today = LocalDate.now(clock);
        StatsService statsService = new StatsService(diaryRepository, currentUserProvider, clock);
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

    @Test
    @DisplayName("최다 기분 동률이면 기분 코드 가나다 순으로 선택한다")
    void 동률_최다기분_가나다순() {
        Long userId = 3L;
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDate today = LocalDate.now(clock);
        StatsService statsService = new StatsService(diaryRepository, currentUserProvider, clock);

        given(currentUserProvider.getCurrentUserId()).willReturn(userId);
        given(diaryRepository.countByUserId(userId)).willReturn(4L);
        given(diaryRepository.countDiariesWithRole(userId, com.example.musing_BE.diary.domain.TrackRole.MY))
                .willReturn(0L);
        given(diaryRepository.existsByUserIdAndDiaryDate(userId, today)).willReturn(false);
        given(diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId)).willReturn(List.of());
        given(diaryRepository.findMoodCountsByUserId(userId))
                .willReturn(List.of(new Object[]{Mood.CALM, 2L}, new Object[]{Mood.JOY, 2L}));

        StatsResponse res = statsService.getStats();

        assertThat(res.topMood()).isEqualTo("기쁨");
    }

    @Test
    @DisplayName("연속 작성일은 월 경계를 넘어도 이어서 계산한다")
    void 연속작성_월경계_계산() {
        Long userId = 4L;
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T15:00:00Z"), ZoneId.of("Asia/Seoul")); // 2026-09-01 KST
        LocalDate today = LocalDate.now(clock);
        StatsService statsService = new StatsService(diaryRepository, currentUserProvider, clock);

        given(currentUserProvider.getCurrentUserId()).willReturn(userId);
        given(diaryRepository.countByUserId(userId)).willReturn(3L);
        given(diaryRepository.countDiariesWithRole(userId, com.example.musing_BE.diary.domain.TrackRole.MY))
                .willReturn(0L);
        given(diaryRepository.existsByUserIdAndDiaryDate(userId, today)).willReturn(true);
        given(diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId))
                .willReturn(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 30)));
        given(diaryRepository.findMoodCountsByUserId(userId)).willReturn(List.of());

        StatsResponse res = statsService.getStats();

        assertThat(res.currentStreak()).isEqualTo(3L);
    }

    @Test
    @DisplayName("오늘 여부 판단은 KST 날짜를 기준으로 조회한다")
    void 오늘여부_KST기준() {
        Long userId = 5L;
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T15:30:00Z"), ZoneId.of("Asia/Seoul")); // 2026-09-01 00:30 KST
        LocalDate kstToday = LocalDate.of(2026, 9, 1);
        StatsService statsService = new StatsService(diaryRepository, currentUserProvider, clock);

        given(currentUserProvider.getCurrentUserId()).willReturn(userId);
        given(diaryRepository.countByUserId(userId)).willReturn(0L);
        given(diaryRepository.countDiariesWithRole(userId, com.example.musing_BE.diary.domain.TrackRole.MY))
                .willReturn(0L);
        given(diaryRepository.existsByUserIdAndDiaryDate(userId, kstToday)).willReturn(true);
        given(diaryRepository.findDiaryDatesByUserIdOrderByDiaryDateDesc(userId)).willReturn(List.of());
        given(diaryRepository.findMoodCountsByUserId(userId)).willReturn(List.of());

        StatsResponse res = statsService.getStats();

        assertThat(res.todayWritten()).isTrue();
        verify(diaryRepository).existsByUserIdAndDiaryDate(userId, kstToday);
    }
}
