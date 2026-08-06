package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.domain.SurveyEntry;
import com.example.musing_BE.track.domain.TrackFilter;
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
import java.util.Optional;
import java.util.Set;

/**
 * 설문 기반 후보 풀 수집 (RECOMMENDATION_STAGE2 §3.6).
 *
 * <p>차트가 못 주는 것을 채운다 — 옛날 곡·인디·발라드처럼 <b>사람들이 실제로 아끼지만
 * 지금 차트에는 없는</b> 가수들. 차트 수집과 파이프라인이 같아서(가수 → 카탈로그 확장)
 * 앞단만 다르다.
 *
 * <pre>
 * 설문 응답("가수 - 곡명") → 곡 검색으로 artistId 특정 → 카탈로그 확장 → tracks(origin=SURVEY)
 * </pre>
 *
 * <p><b>곡명으로 가수를 특정하는 이유</b> — 이름만으로 검색하면 동명이인이 잡힌다.
 * 실측에서 "김광석"이 포크 가수가 아니라 국악 연주자로 해석됐다. 곡명이 함께 오면
 * 그 곡을 부른 가수로 좁혀지고, 곡명이 없으면 기존 이름 검색으로 폴백한다.
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
     * @param entries        설문 응답 줄. {@code "아이유 - 밤편지"} 또는 가수 이름만.
     *                       중복·공백 허용 — 여기서 정리한다.
     * @param storefront     kr/us. null이면 kr
     * @param songsPerArtist 가수당 곡 수. null이면 설정값
     */
    public CollectResult collect(List<String> entries, String storefront, Integer songsPerArtist) {
        Instant start = Instant.now();
        String sf = (storefront == null || storefront.isBlank()) ? "kr" : storefront.trim().toLowerCase();
        int perArtist = (songsPerArtist == null || songsPerArtist <= 0) ? defaultSongsPerArtist : songsPerArtist;

        // 같은 응답이 여러 번 들어와도 한 번만 조회 (표기 공백 정리 포함)
        Set<String> lines = new LinkedHashSet<>();
        for (String n : entries) {
            if (n != null && !n.isBlank()) lines.add(n.trim());
        }

        List<CollectedTrack> candidates = new ArrayList<>();
        Set<Long> seenArtistIds = new LinkedHashSet<>();
        int notFound = 0;

        for (String line : lines) {
            SurveyEntry entry = SurveyEntry.parse(line);
            if (entry == null) continue;

            sleepBetweenCalls();
            Optional<ItunesClient.ItunesArtist> artist = resolveArtist(entry, sf);
            if (artist.isEmpty()) {
                notFound++;
                log.warn("가수를 찾지 못함: {}", line);
                continue;
            }
            Long artistId = artist.get().artistId();
            if (!seenArtistIds.add(artistId)) {
                // 다른 곡을 적었어도 같은 가수면 카탈로그를 다시 받을 필요가 없다
                log.debug("이미 수집한 가수라 건너뜀: {} ({})", artist.get().artistName(), line);
                continue;
            }
            sleepBetweenCalls();
            candidates.addAll(itunesClient.lookupArtistSongs(artistId, perArtist, sf));
        }

        int skipped = 0;
        int inserted = 0;
        int updated = 0;
        for (CollectedTrack c : candidates) {
            if (!TrackFilter.isUsable(c)) {
                skipped++;
                continue;
            }
            if (trackUpsertService.upsert(c, TrackOrigin.SURVEY)) inserted++;
            else updated++;
        }

        CollectResult result = new CollectResult(1, seenArtistIds.size(), candidates.size(), skipped, inserted, updated,
                Duration.between(start, Instant.now()).toSeconds());
        log.info("설문 수집 완료: 응답 {}건 → 가수 {}명 확인({}건 미발견), {}",
                lines.size(), seenArtistIds.size(), notFound, result);
        return result;
    }

    /**
     * 곡명이 있으면 곡으로 가수를 특정하고(동명이인 방지), 실패하면 이름 검색으로 폴백한다.
     */
    private Optional<ItunesClient.ItunesArtist> resolveArtist(SurveyEntry entry, String storefront) {
        if (entry.hasTrack()) {
            Optional<ItunesClient.ItunesArtist> bySong =
                    itunesClient.findArtistBySong(entry.searchTerm(), storefront);
            if (bySong.isPresent()) return bySong;
            log.debug("곡으로 가수를 못 찾아 이름 검색으로 폴백: {}", entry.searchTerm());
            sleepBetweenCalls();
        }
        return itunesClient.searchArtist(entry.artist(), storefront);
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
