package com.example.musing_BE.stats.controller;

import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.stats.dto.StatsResponse;
import com.example.musing_BE.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 5.1 홈 통계. month(YYYY-MM) 선택. */
    @GetMapping
    public ApiResponse<StatsResponse> getStats(
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.getStats(month));
    }
}
