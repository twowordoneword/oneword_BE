package com.example.musing_BE.track.controller;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.security.JwtAuthenticationFilter;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.example.musing_BE.track.dto.TrackSearchResponse;
import com.example.musing_BE.track.service.TrackSearchRateLimiter;
import com.example.musing_BE.track.service.TrackSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.TestPropertySource(properties = {
        "app.auth.jwt-secret=test-jwt-secret-at-least-32-bytes",
        "app.auth.access-token-seconds=3600",
        "app.auth.refresh-token-seconds=1209600",
        "app.auth.google-client-id=test-google-client-id",
        "app.auth.apple-client-id=test-apple-client-id"
})
@DisplayName("TrackSearchController HTTP 계약 테스트")
class TrackSearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TrackSearchService trackSearchService;
    @MockitoBean
    TrackSearchRateLimiter trackSearchRateLimiter;
    @MockitoBean
    CurrentUserProvider currentUserProvider;
    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("정상 검색은 200 + success:true")
    void searchSuccess() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        given(trackSearchService.search("아이유", 20)).willReturn(
                new TrackSearchResponse("아이유", List.of(
                        new TrackInfoResponse("밤편지", "아이유", "밤편지", "art", "preview")
                ))
        );

        mvc.perform(get("/api/v1/tracks/search").param("q", "아이유").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.results[0].name").value("밤편지"));
    }

    @Test
    @DisplayName("요청 한도 초과 시 429 + RATE_LIMIT_EXCEEDED")
    void returns429WhenRateLimitExceeded() throws Exception {
        given(currentUserProvider.getCurrentUserId()).willReturn(1L);
        doThrow(new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(trackSearchRateLimiter).checkOrThrow(anyLong(), anyString());

        mvc.perform(get("/api/v1/tracks/search").param("q", "아이유").param("limit", "20"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
