package com.example.musing_BE.track.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 설문에서 모은 가수 목록 (RECOMMENDATION_STAGE2 §3.6).
 * 엑셀 응답에서 가수 이름만 뽑아 중복 제거한 뒤 보내면 된다.
 */
public record SurveyCollectRequest(
        @NotEmpty(message = "가수 목록이 비어 있습니다.")
        List<String> artists,

        /** 검색 스토어프론트. 국내 가수 위주면 kr. 미지정 시 kr. */
        String storefront,

        /** 가수당 가져올 곡 수. 미지정 시 설정값. */
        Integer songsPerArtist
) {}
