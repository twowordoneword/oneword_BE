package com.example.musing_BE.track.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.musing_BE.common.http.RestClients;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Apple Music RSS 인기곡 차트 (무료·무인증).
 * 실측(RECOMMENDATION_STAGE2 §3.1): 최대 100곡. 200 요청 시 빈 응답.
 * 미리듣기(previewUrl)는 주지 않으므로 트랙 ID를 얻어 {@link ItunesClient#lookupByIds}로 보강한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleChartClient {

    private static final int MAX_LIMIT = 100;

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClients.create("https://rss.marketingtools.apple.com");

    /**
     * 인기곡 차트 조회. 실패 시 예외 대신 빈 목록 — 배치가 다른 스토어프론트를 계속 처리하도록.
     *
     * @param storefront kr, us 등 (소문자)
     * @param limit      1~100 (초과 시 100으로 제한)
     */
    public List<ChartEntry> topSongs(String storefront, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String path = "/api/v2/%s/music/most-played/%d/songs.json".formatted(storefront, capped);
        String raw;
        try {
            raw = restClient.get().uri(path).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("Apple 차트 조회 실패 (storefront={}): {}", storefront, e.getMessage());
            return List.of();
        }
        if (raw == null || raw.isBlank()) {
            log.warn("Apple 차트 응답이 비어 있음 (storefront={}, limit={})", storefront, capped);
            return List.of();
        }
        try {
            ChartResponse res = objectMapper.readValue(raw, ChartResponse.class);
            if (res.feed() == null || res.feed().results() == null) return List.of();
            return res.feed().results();
        } catch (Exception e) {
            log.warn("Apple 차트 파싱 실패 (storefront={}): {}", storefront, e.getMessage());
            return List.of();
        }
    }

    // ----- 응답 매핑 (실측 형태) -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChartResponse(Feed feed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feed(String country, List<ChartEntry> results) {}

    /** id=트랙ID, artistId=아티스트ID(카탈로그 확장의 씨앗). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartEntry(String id, String artistId, String name, String artistName) {

        public Long trackIdAsLong() { return parse(id); }

        public Long artistIdAsLong() { return parse(artistId); }

        private static Long parse(String v) {
            if (v == null || v.isBlank()) return null;
            try {
                return Long.valueOf(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
