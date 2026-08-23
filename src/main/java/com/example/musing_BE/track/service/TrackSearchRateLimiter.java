package com.example.musing_BE.track.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TrackSearchRateLimiter {

    private static final int MAX_ENTRIES_BEFORE_CLEANUP = 10_000;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${musing.track-search.rate-limit.max-requests:30}")
    private int maxRequests;

    @Value("${musing.track-search.rate-limit.window-seconds:60}")
    private long windowSeconds;

    /**
     * 사용자 단위로만 센다.
     *
     * <p>예전에는 userId가 없을 때 {@code X-Forwarded-For}를 키로 썼는데, 이 헤더는 클라이언트가
     * 마음대로 지어낼 수 있어 값만 바꾸면 제한을 무한히 우회할 수 있었다. 이 API는 인증이 필수라
     * userId가 항상 존재하므로 IP 경로 자체를 없앴다.
     */
    public void checkOrThrow(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String key = "u:" + userId;
        long now = System.currentTimeMillis();
        long windowMs = Math.max(windowSeconds, 1) * 1000;
        AtomicInteger updatedCount = new AtomicInteger();

        counters.compute(key, (k, current) -> {
            if (current == null || now - current.windowStartMs >= windowMs) {
                updatedCount.set(1);
                return new WindowCounter(now, 1);
            }
            int next = current.count + 1;
            updatedCount.set(next);
            return new WindowCounter(current.windowStartMs, next);
        });

        if (updatedCount.get() > maxRequests) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        cleanupExpired(now, windowMs);
    }

    private void cleanupExpired(long now, long windowMs) {
        if (counters.size() < MAX_ENTRIES_BEFORE_CLEANUP) {
            return;
        }
        for (Map.Entry<String, WindowCounter> entry : counters.entrySet()) {
            if (now - entry.getValue().windowStartMs >= windowMs) {
                counters.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private record WindowCounter(long windowStartMs, int count) {}
}
