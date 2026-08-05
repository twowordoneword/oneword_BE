package com.example.musing_BE.track.batch;

import com.example.musing_BE.recommendation.client.FreqBlogClient;
import com.example.musing_BE.recommendation.dto.CandidateTrack;
import com.example.musing_BE.track.dto.FeatureFillResult;
import com.example.musing_BE.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 곡 감정값 채우기 배치 (RECOMMENDATION_STAGE2 §4).
 *
 * <p>대상 규칙은 한 줄이다 — <b>감정값이 없고 미리듣기가 있는 곡</b>.
 * 차트·설문·사용자 곡을 구분하지 않는다. 어디서 들어왔든 처리 방식이 같다.
 *
 * <p>감정값은 변하지 않으므로 곡당 한 번만 조회하고 영구 캐시한다.
 * 무료 플랜(월 1,000건)을 넘지 않도록 실행당 상한을 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFillService {

    private static final String SOURCE = "freqblog";

    private final TrackRepository trackRepository;
    private final FreqBlogClient freqBlogClient;
    private final TrackFeatureWriter featureWriter;

    /** 한 번 실행에서 조회할 최대 곡 수 (무료 월 1,000건 보호). */
    @Value("${musing.freqblog.max-per-run:200}")
    private int maxPerRun;

    /** 호출 간 간격 — 동시 6건 제한이 있어 순차 호출하되 약간 쉰다. */
    @Value("${musing.freqblog.request-delay-ms:200}")
    private long requestDelayMs;

    public FeatureFillResult fill() {
        Instant start = Instant.now();
        if (!freqBlogClient.isConfigured()) {
            log.warn("FreqBlog API 키가 없어 감정값 배치를 건너뜁니다.");
            return new FeatureFillResult(0, 0, 0, false, 0);
        }

        List<CandidateTrack> targets = trackRepository.findFeaturePending(Limit.of(Math.max(maxPerRun, 1)));
        int filled = 0;
        int notFound = 0;
        boolean stoppedByQuota = false;

        for (CandidateTrack t : targets) {
            try {
                var features = freqBlogClient.lookup(t.name(), t.artist());
                if (features.isPresent()) {
                    var f = features.get();
                    featureWriter.write(t.id(), f.valence(), f.energy(), f.acousticness(), f.bpm(), SOURCE);
                    filled++;
                } else {
                    // 카탈로그 미보유 곡은 FreqBlog이 뒤에서 분석 중일 수 있다 → 다음 배치에서 다시 시도
                    notFound++;
                }
            } catch (FreqBlogClient.QuotaExceededException e) {
                log.warn("할당량 초과로 배치 중단 — 지금까지 {}곡 채움", filled);
                stoppedByQuota = true;
                break;
            }
            sleep();
        }

        FeatureFillResult result = new FeatureFillResult(targets.size(), filled, notFound, stoppedByQuota,
                Duration.between(start, Instant.now()).toSeconds());
        log.info("감정값 배치 완료: {}", result);
        return result;
    }

    private void sleep() {
        if (requestDelayMs <= 0) return;
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("감정값 배치가 중단되었습니다.", e);
        }
    }
}
