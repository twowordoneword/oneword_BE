package com.example.musing_BE.diary.controller;

import com.example.musing_BE.auth.service.AppJwtService;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Diary/Stats 사용자 격리 통합 테스트")
class DiaryUserIsolationIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AppJwtService appJwtService;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        User userA = userRepository.save(User.create(
                "user-a-" + UUID.randomUUID() + "@musing.app", "userA", "local", "userA-" + UUID.randomUUID()
        ));
        User userB = userRepository.save(User.create(
                "user-b-" + UUID.randomUUID() + "@musing.app", "userB", "local", "userB-" + UUID.randomUUID()
        ));
        userAToken = bearer(userA.getId());
        userBToken = bearer(userB.getId());
    }

    @Test
    @DisplayName("다른 사용자는 같은 날짜 일기를 조회/수정/삭제할 수 없다")
    void cannotReadWriteDeleteOtherUsersDiary() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        String createBody = diaryBody(date, "A의 일기");
        String updateBody = diaryBody(date, "B가 바꾸려는 제목");

        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/diaries/{date}", date)
                        .header("Authorization", userBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));

        mvc.perform(put("/api/v1/diaries/{date}", date)
                        .header("Authorization", userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));

        mvc.perform(delete("/api/v1/diaries/{date}", date)
                        .header("Authorization", userBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));

        mvc.perform(get("/api/v1/diaries/{date}", date)
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("A의 일기"));
    }

    @Test
    @DisplayName("월별 목록과 통계는 사용자별로 분리된다")
    void monthlyAndStatsAreIsolatedPerUser() throws Exception {
        LocalDate userADate = LocalDate.of(2026, 8, 21);
        LocalDate userBDate = LocalDate.of(2026, 8, 22);

        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diaryBody(userADate, "A 일기")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diaryBody(userBDate, "B 일기")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/diaries")
                        .header("Authorization", userAToken)
                        .queryParam("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-08-21"));

        mvc.perform(get("/api/v1/diaries")
                        .header("Authorization", userBToken)
                        .queryParam("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-08-22"));

        mvc.perform(get("/api/v1/stats")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDiaries").value(1))
                .andExpect(jsonPath("$.data.totalMusic").value(0))
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.todayWritten").value(false))
                .andExpect(jsonPath("$.data.topMood").value("평온"));

        mvc.perform(get("/api/v1/stats")
                        .header("Authorization", userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDiaries").value(1))
                .andExpect(jsonPath("$.data.totalMusic").value(0))
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.todayWritten").value(false))
                .andExpect(jsonPath("$.data.topMood").value("평온"));
    }

    private String diaryBody(LocalDate date, String title) {
        return """
                {"date":"%s","title":"%s","body":"body","mood":"평온","weather":"바람"}
                """.formatted(date, title);
    }

    private String bearer(Long userId) {
        return "Bearer " + appJwtService.issueTokenPair(userId).accessToken();
    }
}
