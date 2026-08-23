package com.example.musing_BE.track.service;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrackSearchRateLimiter — 사용자/IP 요청 제한")
class TrackSearchRateLimiterTest {

    @Test
    @DisplayName("윈도우 내 한도를 넘기면 RATE_LIMIT_EXCEEDED를 반환한다")
    void rejectsWhenLimitExceeded() {
        TrackSearchRateLimiter limiter = new TrackSearchRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequests", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60L);

        limiter.checkOrThrow(10L, "127.0.0.1");
        limiter.checkOrThrow(10L, "127.0.0.1");

        assertThatThrownBy(() -> limiter.checkOrThrow(10L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("윈도우가 지나면 카운터가 리셋된다")
    void resetsAfterWindow() throws InterruptedException {
        TrackSearchRateLimiter limiter = new TrackSearchRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequests", 1);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 1L);

        limiter.checkOrThrow(11L, "127.0.0.1");
        Thread.sleep(1100L);

        limiter.checkOrThrow(11L, "127.0.0.1");
    }
}
