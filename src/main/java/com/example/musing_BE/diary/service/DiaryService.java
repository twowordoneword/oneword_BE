package com.example.musing_BE.diary.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.dto.DiaryDetailResponse;
import com.example.musing_BE.diary.dto.DiaryUpsertRequest;
import com.example.musing_BE.diary.dto.MonthlyDiaryResponse;
import com.example.musing_BE.diary.dto.TrackDto;
import com.example.musing_BE.diary.entity.Diary;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.track.entity.Track;
import com.example.musing_BE.track.repository.TrackRepository;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;

    // TODO(auth): 인증 슬라이스에서 SecurityContext의 실제 사용자로 대체
    private static final Long DEV_USER_ID = 1L;

    /** 일기 날짜 기준 타임존 (KST) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 월별 기록 조회 (캘린더). month = "YYYY-MM" */
    public MonthlyDiaryResponse getMonthly(String month) {
        YearMonth ym = YearMonth.parse(month); // 형식 오류 시 DateTimeParseException -> 400
        List<Diary> diaries = diaryRepository.findMonthlyWithTracks(
                DEV_USER_ID, ym.atDay(1), ym.atEndOfMonth());
        return MonthlyDiaryResponse.of(month, diaries);
    }

    /** 특정 날짜 일기 상세 */
    public DiaryDetailResponse getByDate(LocalDate date) {
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(DEV_USER_ID, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
        return toDetail(diary);
    }

    /** 일기 작성 */
    @Transactional
    public DiaryDetailResponse create(DiaryUpsertRequest req) {
        validateNotFuture(req.date());
        if (diaryRepository.existsByUserIdAndDiaryDate(DEV_USER_ID, req.date())) {
            throw new BusinessException(ErrorCode.DIARY_ALREADY_EXISTS);
        }
        User user = userRepository.findById(DEV_USER_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Diary diary = Diary.create(user, req.date(), req.title(), req.body(), req.mood(), req.weather());
        attachTracks(diary, req);
        return toDetail(diaryRepository.save(diary));
    }

    /** 일기 수정 (전체 교체) */
    @Transactional
    public DiaryDetailResponse update(LocalDate date, DiaryUpsertRequest req) {
        if (req.date() != null && !date.equals(req.date())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR); // 경로 날짜 ≠ 바디 날짜
        }
        validateNotFuture(date);
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(DEV_USER_ID, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
        diary.update(req.title(), req.body(), req.mood(), req.weather());
        diary.clearTracks();          // 기존 곡 연결 제거(orphanRemoval)
        diaryRepository.flush();      // DELETE를 INSERT보다 먼저 실행 → uq_diary_role 충돌 방지
        attachTracks(diary, req);     // 새로 설정
        return toDetail(diary);
    }

    /** 일기 삭제 */
    @Transactional
    public void delete(LocalDate date) {
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(DEV_USER_ID, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
        diaryRepository.delete(diary); // diary_tracks는 CASCADE/orphanRemoval로 함께 삭제
    }

    // ----- helpers -----

    /** 미래 날짜 일기 작성 차단. 오늘(KST)까지만 허용. */
    private void validateNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(KST))) {
            throw new BusinessException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
        }
    }

    /** 작성 순번(seq)을 계산해 상세 응답으로 변환. */
    private DiaryDetailResponse toDetail(Diary diary) {
        long seq = diaryRepository.countSeqUpTo(DEV_USER_ID, diary.getId());
        return DiaryDetailResponse.from(diary, seq);
    }

    private void attachTracks(Diary diary, DiaryUpsertRequest req) {
        if (req.myTrack() != null) {
            diary.attachTrack(upsertTrack(req.myTrack()), TrackRole.MY);
        }
        if (req.todayTrack() != null) {
            diary.attachTrack(upsertTrack(req.todayTrack()), TrackRole.RECOMMENDED);
        }
    }

    /** 곡 마스터 upsert: 이미 있으면 메타(앨범/커버/미리듣기) 갱신 후 재사용, 없으면 신규 저장. */
    private Track upsertTrack(TrackDto dto) {
        return trackRepository.findByNameAndArtist(dto.name(), dto.artist())
                .map(existing -> {
                    existing.updateMeta(dto.album(), dto.artworkUrl(), dto.previewUrl());
                    return existing;
                })
                .orElseGet(() -> trackRepository.save(dto.toNewEntity()));
    }
}
