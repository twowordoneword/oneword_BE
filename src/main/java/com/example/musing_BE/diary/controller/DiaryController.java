package com.example.musing_BE.diary.controller;

import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.diary.dto.DiaryDetailResponse;
import com.example.musing_BE.diary.dto.DiaryUpsertRequest;
import com.example.musing_BE.diary.dto.MonthlyDiaryResponse;
import com.example.musing_BE.diary.service.DiaryService;
import jakarta.validation.Valid;
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
