package com.example.musing_BE.track.client;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.dto.CollectedTrack;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * iTunes Search/Lookup API 프록시 (무료·무인증).
 * ⚠️ iTunes는 Content-Type을 text/javascript로 주므로 문자열로 받아 직접 파싱한다.
 * 검색(요청 경로)은 실패 시 502로 변환하고, 수집(배치 경로)은 빈 목록을 돌려 배치가 계속 돌게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItunesClient {

    /** lookup의 id 파라미터에 한 번에 넣을 최대 개수. */
    private static final int LOOKUP_BATCH = 100;

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

    /** 곡 검색. country: KR/US 스토어프론트. (사용자 요청 경로 — 실패 시 502) */
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
            ItunesResult result = objectMapper.readValue(raw, ItunesResult.class);
            if (result.results() == null) {
                return List.of();
            }
            return result.results().stream()
                    .filter(ItunesSong::isSong)
                    .map(ItunesSong::toTrackInfo)
                    .toList();
        } catch (Exception e) {
            log.warn("iTunes 응답 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    /**
     * 가수 이름 → artistId. 설문 응답(이름만 있음)을 카탈로그 확장에 태우기 위한 첫 단계.
     * <p>동명이인이 있을 수 있어 첫 결과를 쓴다. (배치 경로 — 실패 시 empty)
     * <p>⚠️ 응답 필드명은 실호출로 확인 필요(문서 §10).
     */
    public Optional<ItunesArtist> searchArtist(String artistName, String country) {
        if (artistName == null || artistName.isBlank()) return Optional.empty();
        String raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("term", artistName)
                            .queryParam("media", "music")
                            .queryParam("entity", "musicArtist")
                            .queryParam("limit", 1)
                            .queryParam("country", country)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("iTunes 아티스트 검색 실패 (name={}): {}", artistName, e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            ItunesArtistResult result = objectMapper.readValue(raw, ItunesArtistResult.class);
            if (result.results() == null || result.results().isEmpty()) return Optional.empty();
            ItunesArtist first = result.results().get(0);
            return first.artistId() == null ? Optional.empty() : Optional.of(first);
        } catch (Exception e) {
            log.warn("iTunes 아티스트 응답 파싱 실패 (name={}): {}", artistName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 트랙 ID 묶음 조회 — 차트가 주지 않는 previewUrl·앨범·장르를 채운다.
     * (배치 경로 — 실패 시 빈 목록)
     */
    public List<CollectedTrack> lookupByIds(List<Long> trackIds, String country) {
        if (trackIds == null || trackIds.isEmpty()) return List.of();
        List<CollectedTrack> collected = new ArrayList<>();
        for (int i = 0; i < trackIds.size(); i += LOOKUP_BATCH) {
            List<Long> chunk = trackIds.subList(i, Math.min(i + LOOKUP_BATCH, trackIds.size()));
            String ids = chunk.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            collected.addAll(lookup(ids, null, country, "트랙 ID 묶음"));
        }
        return collected;
    }

    /**
     * 아티스트 카탈로그 조회 — 히트곡 외 수록곡까지 후보로 확보한다(§3.2).
     * 응답 첫 항목은 아티스트 정보(wrapperType=artist)이므로 곡만 걸러낸다.
     * (배치 경로 — 실패 시 빈 목록)
     */
    public List<CollectedTrack> lookupArtistSongs(Long artistId, int limit, String country) {
        if (artistId == null) return List.of();
        return lookup(String.valueOf(artistId), limit, country, "아티스트 카탈로그");
    }

    private List<CollectedTrack> lookup(String ids, Integer limit, String country, String what) {
        String raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/lookup")
                                .queryParam("id", ids)
                                .queryParam("entity", "song")
                                .queryParam("country", country);
                        if (limit != null) uriBuilder.queryParam("limit", limit);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("iTunes {} 조회 실패 (id={}): {}", what, ids, e.getMessage());
            return List.of();
        }
        if (raw == null || raw.isBlank()) return List.of();
        try {
            ItunesResult result = objectMapper.readValue(raw, ItunesResult.class);
            if (result.results() == null) return List.of();
            return result.results().stream()
                    .filter(ItunesSong::isSong)   // wrapperType=artist 항목 제외
                    .map(ItunesSong::toCollected)
                    .toList();
        } catch (Exception e) {
            log.warn("iTunes {} 파싱 실패 (id={}): {}", what, ids, e.getMessage());
            return List.of();
        }
    }

    // ----- iTunes 응답 매핑 -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItunesResult(int resultCount, List<ItunesSong> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItunesArtistResult(int resultCount, List<ItunesArtist> results) {}

    /** entity=musicArtist 응답 항목. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItunesArtist(Long artistId, String artistName, String primaryGenreName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItunesSong(
            String wrapperType,
            Long trackId,
            Long artistId,
            String trackName,
            String artistName,
            String collectionName,
            String artworkUrl100,
            String previewUrl,
            String primaryGenreName
    ) {
        /** lookup 응답에는 아티스트 항목이 섞여 오므로 곡만 취한다. */
        boolean isSong() {
            return trackName != null && artistName != null
                    && (wrapperType == null || "track".equals(wrapperType));
        }

        private String artwork600() {
            return artworkUrl100 == null ? null
                    : artworkUrl100.replace("100x100bb", "600x600bb").replace("100x100", "600x600");
        }

        TrackInfoResponse toTrackInfo() {
            return new TrackInfoResponse(trackName, artistName, collectionName, artwork600(), previewUrl);
        }

        CollectedTrack toCollected() {
            return new CollectedTrack(trackId, artistId, trackName, artistName,
                    collectionName, artwork600(), previewUrl, primaryGenreName);
        }
    }
}
