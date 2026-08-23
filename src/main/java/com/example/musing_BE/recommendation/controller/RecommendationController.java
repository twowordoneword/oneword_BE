package com.example.musing_BE.recommendation.controller;

import com.example.musing_BE.common.response.ApiResponse;
import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.Weather;
import com.example.musing_BE.recommendation.dto.RecommendationResponse;
import com.example.musing_BE.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** 3.1 오늘의 곡 추천. mood·weather·date 필수, seedName·seedArtist 선택. */
    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(
            @RequestParam Mood mood,
            @RequestParam Weather weather,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String seedName,
            @RequestParam(required = false) String seedArtist) {
        return ApiResponse.ok(
                recommendationService.recommend(mood, weather, date, seedName, seedArtist));
    }
}
