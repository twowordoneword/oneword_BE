package com.example.musing_BE.recommendation.dto;

import com.example.musing_BE.track.dto.TrackInfoResponse;

/** 추천 응답. 조건 불충족 시 track = null. API_SPEC §3. */
public record RecommendationResponse(TrackInfoResponse track) {}
