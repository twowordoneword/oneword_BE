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
import com.example.musing_BE.track.service.TrackResolver;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final TrackResolver trackResolver;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    /** 일기 날짜 기준 타임존 (KST) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 월별 기록 조회 (캘린더). month = "YYYY-MM" */
    public MonthlyDiaryResponse getMonthly(String month) {
        Long userId = currentUserProvider.getCurrentUserId();
        YearMonth ym = YearMonth.parse(month); // 형식 오류 시 DateTimeParseException -> 400
        List<Diary> diaries = diaryRepository.findMonthlyWithTracks(
                userId, ym.atDay(1), ym.atEndOfMonth());
        return MonthlyDiaryResponse.of(month, diaries);
    }

    /** 특정 날짜 일기 상세 */
    public DiaryDetailResponse getByDate(LocalDate date) {
        Long userId = currentUserProvider.getCurrentUserId();
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
        return toDetail(userId, diary);
    }

    /** 일기 작성 */
    @Transactional
    public DiaryDetailResponse create(DiaryUpsertRequest req) {
        Long userId = currentUserProvider.getCurrentUserId();
        validateNotFuture(req.date());
        if (diaryRepository.existsByUserIdAndDiaryDate(userId, req.date())) {
            throw new BusinessException(ErrorCode.DIARY_ALREADY_EXISTS);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Diary diary = Diary.create(user, req.date(), req.title(), req.body(), req.mood(), req.weather());
        attachTracks(diary, req);
        return toDetail(userId, diaryRepository.save(diary));
    }

    /** 일기 수정 (전체 교체) */
    @Transactional
    public DiaryDetailResponse update(LocalDate date, DiaryUpsertRequest req) {
        Long userId = currentUserProvider.getCurrentUserId();
        if (req.date() != null && !date.equals(req.date())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR); // 경로 날짜 ≠ 바디 날짜
        }
        validateNotFuture(date);
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
        diary.update(req.title(), req.body(), req.mood(), req.weather());
        diary.clearTracks();          // 기존 곡 연결 제거(orphanRemoval)
        diaryRepository.flush();      // DELETE를 INSERT보다 먼저 실행 → uq_diary_role 충돌 방지
        attachTracks(diary, req);     // 새로 설정
        return toDetail(userId, diary);
    }

    /** 일기 삭제 */
    @Transactional
    public void delete(LocalDate date) {
        Long userId = currentUserProvider.getCurrentUserId();
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
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
    private DiaryDetailResponse toDetail(Long userId, Diary diary) {
        long seq = diaryRepository.countSeqUpTo(userId, diary.getId());
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

    /**
     * 곡 마스터 upsert: 이미 있으면 메타(앨범/커버/미리듣기) 갱신 후 재사용, 없으면 신규 저장.
     *
     * <p>서로 다른 사용자가 같은 곡을 동시에 처음 저장하면 조회-저장 사이에서
     * {@code uq_tracks_name_artist}에 걸린다. 그때는 이미 다른 트랜잭션이 행을 만들어 뒀으므로
     * 새 트랜잭션에서 한 번 더 조회하면 성공한다. 두 번째도 실패하면 진짜 충돌이라 그대로 올려보낸다.
     */
    private Track upsertTrack(TrackDto dto) {
        Long trackId;
        try {
            trackId = trackResolver.resolveId(dto);
        } catch (DataIntegrityViolationException e) {
            trackId = trackResolver.resolveId(dto);
        }
        return trackRepository.getReferenceById(trackId);
    }
}
