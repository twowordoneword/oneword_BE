package com.example.musing_BE.track.controller;

import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.track.dto.TrackSearchResponse;
import com.example.musing_BE.track.service.TrackSearchRateLimiter;
import com.example.musing_BE.track.service.TrackSearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracks")
@RequiredArgsConstructor
public class TrackSearchController {

    private final TrackSearchService trackSearchService;
    private final TrackSearchRateLimiter trackSearchRateLimiter;
    private final CurrentUserProvider currentUserProvider;

    /** 4.1 곡 검색 (iTunes 프록시) */
    @GetMapping("/search")
    public ApiResponse<TrackSearchResponse> search(
            HttpServletRequest request,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = currentUserProvider.getCurrentUserId();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = (forwardedFor == null || forwardedFor.isBlank())
                ? request.getRemoteAddr()
                : forwardedFor;
        trackSearchRateLimiter.checkOrThrow(userId, clientIp);
        int capped = Math.min(Math.max(limit, 1), 50);
        return ApiResponse.ok(trackSearchService.search(q, capped));
    }
}
