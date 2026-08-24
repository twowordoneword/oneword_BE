package com.example.musing_BE.diary.controller;

import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.diary.dto.DiaryDetailResponse;
import com.example.musing_BE.diary.dto.DiaryUpsertRequest;
import com.example.musing_BE.diary.dto.DiaryTrackArchiveResponse;
import com.example.musing_BE.diary.dto.MonthlyDiaryResponse;
import com.example.musing_BE.diary.service.DiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    /** 2.1 월별 기록 (캘린더) */
    @GetMapping
    public ApiResponse<MonthlyDiaryResponse> getMonthly(@RequestParam String month) {
        return ApiResponse.ok(diaryService.getMonthly(month));
    }

    /**
     * 2.6 음악 아카이브 — 기록에 담긴 곡을 최신순으로.
     *
     * <p>경로가 {@code /{date}}보다 먼저 잡혀야 하므로 리터럴 매핑이다.
     * Spring은 변수 패턴보다 리터럴을 우선 매칭한다.
     */
    @GetMapping("/tracks")
    public ApiResponse<DiaryTrackArchiveResponse> getTrackArchive(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(diaryService.getTrackArchive(cursor, limit));
    }

    /** 2.2 특정 날짜 상세 */
    @GetMapping("/{date}")
    public ApiResponse<DiaryDetailResponse> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(diaryService.getByDate(date));
    }

    /** 2.3 작성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiaryDetailResponse> create(@Valid @RequestBody DiaryUpsertRequest req) {
        return ApiResponse.ok(diaryService.create(req));
    }

    /** 2.4 수정 (전체 교체) */
    @PutMapping("/{date}")
    public ApiResponse<DiaryDetailResponse> update(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DiaryUpsertRequest req) {
        return ApiResponse.ok(diaryService.update(date, req));
    }

    /** 2.5 삭제 */
    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        diaryService.delete(date);
    }
}
