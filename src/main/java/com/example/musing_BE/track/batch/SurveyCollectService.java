package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectResult;
import com.example.musing_BE.track.dto.CollectedTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 설문 기반 후보 풀 수집 (RECOMMENDATION_STAGE2 §3.6).
 *
 * <p>차트가 못 주는 것을 채운다 — 옛날 곡·인디·발라드처럼 <b>사람들이 실제로 아끼지만
 * 지금 차트에는 없는</b> 가수들. 차트 수집과 파이프라인이 같아서(가수 → 카탈로그 확장)
 * 앞단만 다르다.
 *
 * <pre>
 * 설문 가수 이름 → iTunes 아티스트 검색(artistId) → 카탈로그 확장 → tracks(origin=SURVEY)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyCollectService {

    private final ItunesClient itunesClient;
    private final TrackUpsertService trackUpsertService;

    @Value("${musing.chart.songs-per-artist:50}")
    private int defaultSongsPerArtist;

    @Value("${musing.chart.request-delay-ms:3000}")
    private long requestDelayMs;

    /**
     * @param artistNames   설문에서 모은 가수 이름 (중복·공백 허용 — 여기서 정리한다)
     * @param storefront    kr/us. null이면 kr
     * @param songsPerArtist 가수당 곡 수. null이면 설정값
     */
    public CollectResult collect(List<String> artistNames, String storefront, Integer songsPerArtist) {
        Instant start = Instant.now();
        String sf = (storefront == null || storefront.isBlank()) ? "kr" : storefront.trim().toLowerCase();
        int perArtist = (songsPerArtist == null || songsPerArtist <= 0) ? defaultSongsPerArtist : songsPerArtist;

        // 표기가 흔들려도(공백·대소문자) 같은 이름은 한 번만 조회
        Set<String> names = new LinkedHashSet<>();
        for (String n : artistNames) {
            if (n != null && !n.isBlank()) names.add(n.trim());
        }

        List<CollectedTrack> candidates = new ArrayList<>();
        int resolved = 0;
        int notFound = 0;

        for (String name : names) {
            sleepBetweenCalls();
            var artist = itunesClient.searchArtist(name, sf);
            if (artist.isEmpty()) {
                notFound++;
                log.warn("가수를 찾지 못함: {}", name);
                continue;
            }
            resolved++;
            sleepBetweenCalls();
            candidates.addAll(itunesClient.lookupArtistSongs(artist.get().artistId(), perArtist, sf));
        }

        int skipped = 0;
        int inserted = 0;
        int updated = 0;
        for (CollectedTrack c : candidates) {
            if (!isUsable(c)) {
                skipped++;
                continue;
            }
            if (trackUpsertService.upsert(c, TrackOrigin.SURVEY)) inserted++;
            else updated++;
        }

        CollectResult result = new CollectResult(1, resolved, candidates.size(), skipped, inserted, updated,
                Duration.between(start, Instant.now()).toSeconds());
        log.info("설문 수집 완료: 요청 가수 {}명 중 {}명 확인({}명 미발견), {}", names.size(), resolved, notFound, result);
        return result;
    }

    /** 미리듣기가 없으면 감정값 분석도 앱 재생도 불가하므로 제외 (§3.5). */
    private boolean isUsable(CollectedTrack c) {
        if (c.name() == null || c.artist() == null) return false;
        if (c.previewUrl() == null || c.previewUrl().isBlank()) return false;
        String album = c.album() == null ? "" : c.album();
        return !album.contains("DJ Mix");
    }

    private void sleepBetweenCalls() {
        if (requestDelayMs <= 0) return;
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("설문 수집 배치가 중단되었습니다.", e);
        }
    }
}
