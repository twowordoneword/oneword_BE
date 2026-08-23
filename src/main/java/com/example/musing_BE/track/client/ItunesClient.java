package com.example.musing_BE.track.client;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.dto.CollectedTrack;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.musing_BE.common.http.RestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

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
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;

    @Value("${musing.track-search.external.bulkhead.max-concurrent:20}")
    private int maxConcurrentCalls;

    @Value("${musing.track-search.external.retry.backoff-ms:120}")
    private long retryBackoffMs;

    @Value("${musing.track-search.external.circuit.failure-threshold:5}")
    private int circuitFailureThreshold;

    @Value("${musing.track-search.external.circuit.open-seconds:30}")
    private long circuitOpenSeconds;

    private volatile CircuitState circuitState = new CircuitState(0, Instant.EPOCH);
    private Semaphore searchBulkhead;

    private final RestClient restClient = RestClients.create(
            "https://itunes.apple.com", Duration.ofSeconds(2), Duration.ofSeconds(3));

    @PostConstruct
    void initBulkhead() {
        searchBulkhead = new Semaphore(Math.max(maxConcurrentCalls, 1), true);
    }

    /** 곡 검색. country: KR/US 스토어프론트. (사용자 요청 경로 — 실패 시 502) */
    public List<TrackInfoResponse> searchSongs(String term, int limit, String country) {
        String raw;
        if (isCircuitOpen()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_UNAVAILABLE);
        }
        if (!searchBulkhead.tryAcquire()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_UNAVAILABLE);
        }
        try {
            raw = executeSearchWithRetry(term, limit, country);
        } finally {
            searchBulkhead.release();
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
            recordFailure();
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private String executeSearchWithRetry(String term, int limit, String country) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/search")
                                .queryParam("term", term)
                                .queryParam("media", "music")
                                .queryParam("entity", "song")
                                .queryParam("limit", limit)
                                .queryParam("country", country)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            int status = res.getStatusCode().value();
                            if (status >= 500) {
                                throw new RetryableExternalException("iTunes 5xx: " + status);
                            }
                            throw new NonRetryableExternalException("iTunes 4xx: " + status);
                        })
                        .body(String.class);
                recordSuccess();
                return body;
            } catch (RetryableExternalException | ResourceAccessException e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    pauseRetryBackoff(attempt);
                    continue;
                }
                recordFailure();
                throw mapToBusinessException(e, term);
            } catch (NonRetryableExternalException e) {
                recordFailure();
                throw mapToBusinessException(e, term);
            } catch (BusinessException e) {
                recordFailure();
                throw e;
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    pauseRetryBackoff(attempt);
                    continue;
                }
                recordFailure();
                throw mapToBusinessException(e, term);
            }
        }
        recordFailure();
        throw mapToBusinessException(lastException, term);
    }

    private void pauseRetryBackoff(int attempt) {
        long waitMillis = Math.max(retryBackoffMs, 20) * attempt;
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EXTERNAL_API_UNAVAILABLE);
        }
    }

    private BusinessException mapToBusinessException(Exception exception, String term) {
        if (exception instanceof ResourceAccessException) {
            log.warn("iTunes 검색 타임아웃/연결 실패 (queryLength={}): {}", safeLength(term), exception.getMessage());
            return new BusinessException(ErrorCode.EXTERNAL_API_TIMEOUT);
        }
        log.warn("iTunes 검색 실패 (queryLength={}): {}", safeLength(term), exception == null ? "unknown" : exception.getMessage());
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }

    private int safeLength(String term) {
        return term == null ? 0 : Math.min(term.length(), 256);
    }

    private synchronized void recordSuccess() {
        circuitState = new CircuitState(0, Instant.EPOCH);
    }

    private synchronized void recordFailure() {
        int next = circuitState.consecutiveFailures + 1;
        Instant openedUntil = circuitState.openedUntil;
        if (next >= Math.max(circuitFailureThreshold, 1)) {
            openedUntil = Instant.now().plusSeconds(Math.max(circuitOpenSeconds, 1));
            next = 0;
        }
        circuitState = new CircuitState(next, openedUntil);
    }

    private boolean isCircuitOpen() {
        return circuitState.openedUntil.isAfter(Instant.now());
    }

    private record CircuitState(int consecutiveFailures, Instant openedUntil) {}

    private static class RetryableExternalException extends RuntimeException {
        RetryableExternalException(String message) {
            super(message);
        }
    }

    private static class NonRetryableExternalException extends RuntimeException {
        NonRetryableExternalException(String message) {
            super(message);
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
     * 곡으로 아티스트 특정 — <b>동명이인 방지</b>.
     * <p>"김광석"으로 아티스트를 검색하면 포크 가수가 아니라 국악 연주자가 나올 수 있다(실측).
     * "김광석 서른 즈음에"처럼 곡까지 넣어 검색하면 그 곡을 부른 가수로 좁혀진다.
     *
     * @param term "가수 곡명" 형태의 질의어
     * @return 첫 결과의 artistId·artistName. 못 찾으면 empty (배치 경로 — 예외 대신 empty)
     */
    public Optional<ItunesArtist> findArtistBySong(String term, String country) {
        if (term == null || term.isBlank()) return Optional.empty();
        String raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("term", term)
                            .queryParam("media", "music")
                            .queryParam("entity", "song")
                            .queryParam("limit", 1)
                            .queryParam("country", country)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("iTunes 곡 기반 아티스트 검색 실패 (term={}): {}", term, e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            ItunesResult result = objectMapper.readValue(raw, ItunesResult.class);
            if (result.results() == null || result.results().isEmpty()) return Optional.empty();
            ItunesSong first = result.results().get(0);
            if (first.artistId() == null) return Optional.empty();
            return Optional.of(new ItunesArtist(first.artistId(), first.artistName(), first.primaryGenreName()));
        } catch (Exception e) {
            log.warn("iTunes 곡 기반 아티스트 응답 파싱 실패 (term={}): {}", term, e.getMessage());
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
