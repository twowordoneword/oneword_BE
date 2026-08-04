package com.example.musing_BE.track.dto;

import java.util.List;

/** 검색 응답. API_SPEC §4. */
public record TrackSearchResponse(
        String query,
        List<TrackInfoResponse> results
) {}
