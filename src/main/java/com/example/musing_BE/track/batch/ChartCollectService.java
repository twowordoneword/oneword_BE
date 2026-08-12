package com.example.musing_BE.track.batch;

import com.example.musing_BE.common.util.Throttle;
import com.example.musing_BE.track.client.AppleChartClient;
import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectResult;
import com.example.musing_BE.track.dto.CollectedTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 후보 풀 수집 배치 (RECOMMENDATION_STAGE2 §3).
 *
 * <p>차트를 곡 목록이 아니라 <b>아티스트 발굴 목록</b>으로 쓴다.
 * 인기 top 100만 담으면 다 아는 노래뿐이므로, 차트에서 아티스트를 뽑아 카탈로그를 펼쳐
 * 수록곡까지 후보에 넣는다.
 *
 * <pre>
 * RSS 차트(kr, us) → 트랙 ID·아티스트 ID 추출
 *                  → 트랙 ID 묶음 lookup (previewUrl 확보)
 *                  → 아티스트별 카탈로그 lookup (수록곡 확보)
 *                  → 필터 → tracks upsert (origin=CHART)
 * </pre>
 *
 * <p>⚠️ iTunes는 분당 약 20콜 제한이 있어 카탈로그 조회 사이에 지연을 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartCollectService {

    private final AppleChartClient appleChartClient;
    private final ItunesClient itunesClient;
    private final TrackCollector trackCollector;

    @Value("${musing.chart.storefronts:kr,us}")
    private List<String> storefronts;

    @Value("${musing.chart.limit:100}")
    private int chartLimit;

    @Value("${musing.chart.songs-per-artist:50}")
    private int songsPerArtist;

    /** iTunes 분당 20콜 제한 대응 (3초 = 분당 20회). */
    @Value("${musing.chart.request-delay-ms:3000}")
    private long requestDelayMs;

    /** 수집 실행. 외부 호출이 많아 전체를 한 트랜잭션으로 묶지 않는다. */
    public CollectResult collect() {
        Instant start = Instant.now();
        // 아티스트 ID → 어느 스토어프론트에서 발견했는지(카탈로그 조회 시 같은 스토어 사용)
        Map<Long, String> artistStorefront = new LinkedHashMap<>();
        List<CollectedTrack> candidates = new ArrayList<>();

        for (String storefront : storefronts) {
            String sf = storefront.trim().toLowerCase();
            List<AppleChartClient.ChartEntry> chart = appleChartClient.topSongs(sf, chartLimit);
            if (chart.isEmpty()) {
                log.warn("차트가 비어 있어 건너뜀 (storefront={})", sf);
                continue;
            }
            List<Long> trackIds = new ArrayList<>();
            for (AppleChartClient.ChartEntry e : chart) {
                Long trackId = e.trackIdAsLong();
                if (trackId != null) trackIds.add(trackId);
                Long artistId = e.artistIdAsLong();
                if (artistId != null) artistStorefront.putIfAbsent(artistId, sf);
            }
            // 차트 곡 자체도 미리듣기를 받아 후보에 넣는다
            candidates.addAll(itunesClient.lookupByIds(trackIds, sf));
            log.info("차트 수집: storefront={}, 곡={}, 누적 아티스트={}", sf, chart.size(), artistStorefront.size());
        }

        // 아티스트 카탈로그 확장 — 여기서 후보가 수십 배로 늘어난다
        for (Map.Entry<Long, String> entry : artistStorefront.entrySet()) {
            Throttle.pause(requestDelayMs);
            candidates.addAll(itunesClient.lookupArtistSongs(entry.getKey(), songsPerArtist, entry.getValue()));
        }

        int fetched = candidates.size();
        TrackCollector.Counts counts = trackCollector.upsertAll(candidates, TrackOrigin.CHART);

        CollectResult result = new CollectResult(artistStorefront.size(),
                fetched, counts.skipped(), counts.inserted(), counts.updated(),
                java.time.Duration.between(start, Instant.now()).toSeconds());
        log.info("수집 완료: {}", result);
        return result;
    }

}
