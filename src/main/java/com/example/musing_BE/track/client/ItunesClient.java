package com.example.musing_BE.track.client;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * iTunes Search API 프록시 (무료·무인증).
 * ⚠️ iTunes는 Content-Type을 text/javascript로 주므로 문자열로 받아 직접 파싱한다.
 * 타임아웃·에러 처리 포함 — iTunes 장애 시 502(EXTERNAL_API_ERROR)로 변환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItunesClient {

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://itunes.apple.com")
            .requestFactory(timeoutFactory())
            .build();

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 연결 3초
        factory.setReadTimeout(5000);    // 응답 5초
        return factory;
    }

    /** 곡 검색. country: KR/US 스토어프론트. */
    public List<TrackInfoResponse> searchSongs(String term, int limit, String country) {
        String raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("term", term)
                            .queryParam("media", "music")
                            .queryParam("entity", "song")
                            .queryParam("limit", limit)
                            .queryParam("country", country)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("iTunes 검색 실패 (term={}): {}", term, e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            ItunesSearchResult result = objectMapper.readValue(raw, ItunesSearchResult.class);
            if (result.results() == null) {
                return List.of();
            }
            return result.results().stream()
                    .map(ItunesSong::toTrackInfo)
                    .toList();
        } catch (Exception e) {
            log.warn("iTunes 응답 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    // ----- iTunes 응답 매핑 -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItunesSearchResult(int resultCount, List<ItunesSong> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItunesSong(
            String trackName,
            String artistName,
            String collectionName,
            String artworkUrl100,
            String previewUrl
    ) {
        TrackInfoResponse toTrackInfo() {
            String art = artworkUrl100 == null ? null
                    : artworkUrl100.replace("100x100bb", "600x600bb").replace("100x100", "600x600");
            return new TrackInfoResponse(trackName, artistName, collectionName, art, previewUrl);
        }
    }
}
