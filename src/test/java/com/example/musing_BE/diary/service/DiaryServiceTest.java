package com.example.musing_BE.diary.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.diary.dto.DiaryUpsertRequest;
import com.example.musing_BE.diary.dto.TrackDto;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.track.entity.Track;
import com.example.musing_BE.track.repository.TrackRepository;
import com.example.musing_BE.track.service.TrackResolver;
import com.example.musing_BE.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiaryService 비즈니스 로직 테스트")
class DiaryServiceTest {

    @Mock DiaryRepository diaryRepository;
    @Mock TrackRepository trackRepository;
    @Mock UserRepository userRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock TrackResolver trackResolver;
    @InjectMocks DiaryService diaryService;

    private final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private final LocalDate PAST = LocalDate.of(2020, 1, 1);

    private DiaryUpsertRequest req(LocalDate date) {
        return new DiaryUpsertRequest(date, "제목", "본문", Mood.JOY, Weather.SUNNY, null, null);
    }

    /**
     * 서로 다른 사용자가 같은 곡을 동시에 처음 저장하면 조회-저장 사이에서 유니크 제약에 걸린다.
     * 그때 요청이 실패로 끝나면 안 되고, 이미 만들어진 행을 재조회해 이어가야 한다.
     */
    @Test
    @DisplayName("곡 저장이 유니크 충돌로 실패하면 새 트랜잭션에서 재조회해 이어간다")
    void 곡_동시저장_충돌시_재시도() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        given(diaryRepository.existsByUserIdAndDiaryDate(anyLong(), any())).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(
                User.create("a@musing.app", "nick", "kakao", "pid")));
        given(trackResolver.resolveId(any(TrackDto.class)))
                .willThrow(new DataIntegrityViolationException("uq_tracks_name_artist"))
                .willReturn(42L);
        given(trackRepository.getReferenceById(42L)).willReturn(Track.builder()
                .name("밤편지").artist("아이유").build());
        given(diaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        DiaryUpsertRequest request = new DiaryUpsertRequest(
                PAST, "제목", "본문", Mood.JOY, Weather.SUNNY,
                new TrackDto(null, "밤편지", "아이유", null, null, null), null);

        assertThat(diaryService.create(request)).isNotNull();
        verify(trackResolver, times(2)).resolveId(any(TrackDto.class));
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
