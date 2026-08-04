package com.example.musing_BE.diary.controller;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.diary.dto.DiaryDetailResponse;
import com.example.musing_BE.diary.service.DiaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiaryController.class)
@DisplayName("DiaryController HTTP 계약 테스트")
class DiaryControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DiaryService diaryService;

    @Test
    @DisplayName("정상 작성 → 201 + success:true")
    void 작성_성공_201() throws Exception {
        DiaryDetailResponse resp = new DiaryDetailResponse(
                1L, 1L, LocalDate.of(2026, 7, 21), "여름", "바람",
                Mood.CALM, Weather.WIND, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        given(diaryService.create(any())).willReturn(resp);

        String body = """
                {"date":"2026-07-21","title":"여름","body":"바람","mood":"평온","weather":"바람"}
                """;

        mvc.perform(post("/api/v1/diaries")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seq").value(1))
                .andExpect(jsonPath("$.data.mood").value("평온"));
    }

    @Test
    @DisplayName("잘못된 mood 값 → 400 VALIDATION_ERROR")
    void 잘못된_mood_400() throws Exception {
        String body = """
                {"date":"2026-07-21","mood":"행복","weather":"맑음"}
                """;

        mvc.perform(post("/api/v1/diaries")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("없는 일기 조회 → 404 DIARY_NOT_FOUND")
    void 없는_일기_404() throws Exception {
        given(diaryService.getByDate(any()))
                .willThrow(new BusinessException(ErrorCode.DIARY_NOT_FOUND));

        mvc.perform(get("/api/v1/diaries/2026-07-21"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
    }
}
