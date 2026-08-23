package com.example.musing_BE.track.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.example.musing_BE.track.dto.TrackSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackSearchService — 캐시/장애 격리")
class TrackSearchServiceTest {

    @Mock
    ItunesClient itunesClient;

    @InjectMocks
    TrackSearchService trackSearchService;

    @Test
    @DisplayName("같은 조건 반복 검색은 TTL 내 캐시를 재사용한다")
    void reusesCacheWithinTtl() {
        ReflectionTestUtils.setField(trackSearchService, "cacheTtlSeconds", 60L);
        List<TrackInfoResponse> results = List.of(
                new TrackInfoResponse("밤편지", "아이유", "밤편지", "art", "preview")
        );
        given(itunesClient.searchSongs("아이유", 20, "KR")).willReturn(results);

        TrackSearchResponse first = trackSearchService.search("아이유", 20);
        TrackSearchResponse second = trackSearchService.search("아이유", 20);

        assertThat(first.results()).hasSize(1);
        assertThat(second.results()).hasSize(1);
        verify(itunesClient, times(1)).searchSongs("아이유", 20, "KR");
    }

    @Test
    @DisplayName("외부 API 타임아웃 시 stale 캐시를 반환한다")
    void returnsStaleCacheOnTimeout() throws InterruptedException {
        ReflectionTestUtils.setField(trackSearchService, "cacheTtlSeconds", 1L);
        List<TrackInfoResponse> results = List.of(
                new TrackInfoResponse("좋은날", "아이유", "Real", "art", "preview")
        );
        given(itunesClient.searchSongs("아이유", 20, "KR"))
                .willReturn(results)
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT));

        TrackSearchResponse cached = trackSearchService.search("아이유", 20);
        Thread.sleep(1100L);
        TrackSearchResponse fallback = trackSearchService.search("아이유", 20);

        assertThat(cached.results()).hasSize(1);
        assertThat(fallback.results()).hasSize(1);
        verify(itunesClient, times(2)).searchSongs("아이유", 20, "KR");
    }

    @Test
    @DisplayName("캐시가 없으면 외부 타임아웃 에러를 그대로 반환한다")
    void throwsTimeoutWhenNoCache() {
        ReflectionTestUtils.setField(trackSearchService, "cacheTtlSeconds", 60L);
        given(itunesClient.searchSongs("new", 20, "KR"))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT));

        assertThatThrownBy(() -> trackSearchService.search("new", 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXTERNAL_API_TIMEOUT);
    }
}
