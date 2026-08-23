package com.example.musing_BE.diary.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.diary.dto.DiaryUpsertRequest;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.track.repository.TrackRepository;
import com.example.musing_BE.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiaryService 비즈니스 로직 테스트")
class DiaryServiceTest {

    @Mock DiaryRepository diaryRepository;
    @Mock TrackRepository trackRepository;
    @Mock UserRepository userRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks DiaryService diaryService;

    private final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private final LocalDate PAST = LocalDate.of(2020, 1, 1);

    private DiaryUpsertRequest req(LocalDate date) {
        return new DiaryUpsertRequest(date, "제목", "본문", Mood.JOY, Weather.SUNNY, null, null);
    }

    @Test
    @DisplayName("미래 날짜로 작성하면 FUTURE_DATE_NOT_ALLOWED")
    void 미래날짜_작성_차단() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        assertThatThrownBy(() -> diaryService.create(req(TODAY.plusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FUTURE_DATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("같은 날짜에 이미 일기가 있으면 DIARY_ALREADY_EXISTS")
    void 같은날짜_중복_차단() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        given(diaryRepository.existsByUserIdAndDiaryDate(anyLong(), any())).willReturn(true);

        assertThatThrownBy(() -> diaryService.create(req(PAST)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DIARY_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("없는 날짜 조회하면 DIARY_NOT_FOUND")
    void 없는날짜_조회_404() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        given(diaryRepository.findByUserIdAndDiaryDate(anyLong(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> diaryService.getByDate(PAST))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DIARY_NOT_FOUND));
    }

    @Test
    @DisplayName("수정 시 경로 날짜와 바디 날짜가 다르면 VALIDATION_ERROR")
    void 수정_날짜불일치_400() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        LocalDate pathDate = LocalDate.of(2020, 1, 1);
        DiaryUpsertRequest bodyReq = req(LocalDate.of(2020, 1, 2)); // 다른 날짜

        assertThatThrownBy(() -> diaryService.update(pathDate, bodyReq))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
