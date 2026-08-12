package com.example.musing_BE.track.client;

import com.example.musing_BE.common.http.RestClients;
import com.example.musing_BE.track.client.dto.FreqBlogFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * FreqBlog 음악 특성 API (곡 감정값 소스, RECOMMENDATION_STAGE2 §1).
 *
 * <p>이름+가수로 조회한다 — 곡 ID 매칭이 필요 없다. 카탈로그에 없는 곡은 FreqBlog이
 * iTunes 미리듣기를 받아 분석·등재하므로(약 15초), <b>첫 조회에서 값이 없어도 정상</b>이다.
 * 다음 배치에서 다시 물어보면 채워져 있다.
 *
 * <p>무료 플랜: 월 1,000건, /lookup 동시 6건. 초과 시 429.
 *
 * <p>⚠️ {@code POST /bulk}(50곡 묶음)로 바꾸면 호출 수를 줄일 수 있으나 요청 본문 형식이
 * 공개 문서에 없어, 검증된 {@code GET /lookup}으로 구현했다. 할당량 소모는 동일하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreqBlogClient {

    private final ObjectMapper objectMapper;

    @Value("${musing.freqblog.api-key:}")
    private String apiKey;

    // 카탈로그에 없는 곡은 분석이 붙어 느릴 수 있어 응답 대기를 길게 잡는다
    private final RestClient restClient = RestClients.create(
            "https://api.freqblog.com", Duration.ofSeconds(3), Duration.ofSeconds(10));

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 곡 감정값 조회.
     *
     * @return 값이 없거나(미등재·분석 중) 실패하면 empty
     * @throws QuotaExceededException 429 — 배치를 즉시 멈춰야 한다
     */
    public Optional<FreqBlogFeatures> lookup(String trackName, String artistName) {
        if (!isConfigured()) {
            log.warn("FreqBlog API 키가 없어 조회를 건너뜁니다 (musing.freqblog.api-key)");
            return Optional.empty();
        }
        String raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/lookup")
                            .queryParam("track", trackName)
                            .queryParam("artist", artistName)
                            .build())
                    .header("X-Api-Key", apiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 429) {
                            throw new QuotaExceededException();
                        }
                        throw new IllegalStateException("FreqBlog 응답 " + res.getStatusCode());
                    })
                    .body(String.class);
        } catch (QuotaExceededException e) {
            throw e;   // 배치가 중단 처리하도록 그대로 올린다
        } catch (Exception e) {
            log.debug("FreqBlog 조회 실패 ({} - {}): {}", artistName, trackName, e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            FreqBlogFeatures f = objectMapper.readValue(raw, FreqBlogFeatures.class);
            return f.usable() ? Optional.of(f) : Optional.empty();
        } catch (Exception e) {
            log.warn("FreqBlog 응답 파싱 실패 ({} - {}): {}", artistName, trackName, e.getMessage());
            return Optional.empty();
        }
    }

    /** 월 할당량 또는 동시 요청 한도 초과. 배치를 멈추고 다음 실행을 기다린다. */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException() {
            super("FreqBlog 할당량 초과(429)");
        }
    }
}
