package com.example.musing_BE.track.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.example.musing_BE.track.dto.TrackSearchResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackSearchService {

    private static final int MAX_CACHE_ENTRIES = 2_000;

    private final ItunesClient itunesClient;
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${musing.track-search.cache.ttl-seconds:30}")
    private long cacheTtlSeconds;

    public TrackSearchResponse search(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        CacheKey key = new CacheKey(normalizedQuery, limit);
        Instant now = Instant.now();

        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired(now)) {
            return cached.response();
        }

        try {
            List<TrackInfoResponse> results = itunesClient.searchSongs(normalizedQuery, limit, "KR");
            TrackSearchResponse response = new TrackSearchResponse(normalizedQuery, results);
            cache.put(key, new CacheEntry(response, now.plusSeconds(Math.max(cacheTtlSeconds, 1))));
            cleanupIfNeeded(now);
            return response;
        } catch (BusinessException e) {
            if (cached != null && isExternalFailure(e.getErrorCode())) {
                log.warn("iTunes 검색 장애로 stale cache 반환 (query='{}', limit={})", normalizedQuery, limit);
                return cached.response();
            }
            throw e;
        }
    }

    private boolean isExternalFailure(ErrorCode errorCode) {
        return errorCode == ErrorCode.EXTERNAL_API_ERROR
                || errorCode == ErrorCode.EXTERNAL_API_TIMEOUT
                || errorCode == ErrorCode.EXTERNAL_API_UNAVAILABLE;
    }

    private void cleanupIfNeeded(Instant now) {
        if (cache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record CacheKey(String query, int limit) {}

    private record CacheEntry(TrackSearchResponse response, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
