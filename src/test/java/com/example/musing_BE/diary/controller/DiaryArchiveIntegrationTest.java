package com.example.musing_BE.diary.controller;

import com.example.musing_BE.auth.service.AppJwtService;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("음악 아카이브 통합 테스트")
class DiaryArchiveIntegrationTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 8, 2);
    private static final LocalDate DAY3 = LocalDate.of(2026, 8, 3);

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AppJwtService appJwtService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        User user = userRepository.save(User.create(
                "archive-" + UUID.randomUUID() + "@musing.app", "아카이브", "local", UUID.randomUUID().toString()));
        token = "Bearer " + appJwtService.issueTokenPair(user.getId()).accessToken();

        // DAY2에만 곡 두 개 — 같은 날짜가 두 번 나오는 경우를 만든다
        createDiary(DAY1, "\"myTrack\":{\"name\":\"곡A\",\"artist\":\"가수A\"}");
        createDiary(DAY2, "\"myTrack\":{\"name\":\"곡B\",\"artist\":\"가수B\"},"
                + "\"todayTrack\":{\"name\":\"곡C\",\"artist\":\"가수C\"}");
        createDiary(DAY3, "\"myTrack\":{\"name\":\"곡D\",\"artist\":\"가수D\"}");
    }

    @Test
    @DisplayName("최신순으로 모든 곡을 돌려주고, 곡이 둘인 날짜는 두 번 나온다")
    void returnsAllTracksNewestFirst() throws Exception {
        mvc.perform(get("/api/v1/diaries/tracks").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].date").value("2026-08-03"))
                .andExpect(jsonPath("$.data.items[1].date").value("2026-08-02"))
                .andExpect(jsonPath("$.data.items[2].date").value("2026-08-02"))
                .andExpect(jsonPath("$.data.items[3].date").value("2026-08-01"))
                .andExpect(jsonPath("$.data.items[0].name").value("곡D"))
                .andExpect(jsonPath("$.data.items[0].mood").value("평온"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    /** 커서가 날짜만 담으면 곡이 둘인 날짜에서 한 곡이 누락되거나 중복된다. */
    @Test
    @DisplayName("커서로 이어 읽으면 같은 날짜가 겹치거나 빠지지 않는다")
    void paginatesWithoutGapOrDuplicate() throws Exception {
        String first = mvc.perform(get("/api/v1/diaries/tracks")
                        .header("Authorization", token)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].date").value("2026-08-03"))
                .andExpect(jsonPath("$.data.items[1].date").value("2026-08-02"))
                .andReturn().getResponse().getContentAsString();

        String cursor = JsonPath.read(first, "$.data.nextCursor");
        assertThat(cursor).isNotBlank();
        String firstPageSecondItem = JsonPath.read(first, "$.data.items[1].name");

        String second = mvc.perform(get("/api/v1/diaries/tracks")
                        .header("Authorization", token)
                        .queryParam("limit", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].date").value("2026-08-02"))
                .andExpect(jsonPath("$.data.items[1].date").value("2026-08-01"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // 08-02에 곡이 둘이므로 페이지 경계가 그 날짜 한가운데를 지난다 — 중복되면 안 된다
        String secondPageFirstItem = JsonPath.read(second, "$.data.items[0].name");
        assertThat(secondPageFirstItem).isNotEqualTo(firstPageSecondItem);
    }

    @Test
    @DisplayName("통계의 totalMusic이 아카이브 개수와 일치한다")
    void totalMusicMatchesArchiveSize() throws Exception {
        mvc.perform(get("/api/v1/stats").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMusic").value(4));
    }

    @Test
    @DisplayName("형식이 깨진 커서는 400")
    void rejectsMalformedCursor() throws Exception {
        mvc.perform(get("/api/v1/diaries/tracks")
                        .header("Authorization", token)
                        .queryParam("cursor", "무슨값"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("limit 범위를 벗어나면 400")
    void rejectsOutOfRangeLimit() throws Exception {
        mvc.perform(get("/api/v1/diaries/tracks")
                        .header("Authorization", token)
                        .queryParam("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private void createDiary(LocalDate date, String tracksJson) throws Exception {
        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","title":"제목","body":"본문","mood":"평온","weather":"맑음",%s}
                                """.formatted(date, tracksJson)))
                .andExpect(status().isCreated());
    }
}
