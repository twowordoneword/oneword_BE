package com.example.musing_BE.track.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 설문 응답 목록 (RECOMMENDATION_STAGE2 §3.6).
 *
 * <p>엑셀 응답을 그대로 넣으면 된다. {@code "아이유 - 밤편지"} 처럼 곡명이 함께 오면
 * <b>동명이인을 피해</b> 정확한 가수를 찾는다. 가수 이름만 있어도 동작한다.
 *
 * <pre>
 * { "artists": ["아이유 - 밤편지", "김광석 - 서른 즈음에", "넬"], "songsPerArtist": 10 }
 * </pre>
 */
public record SurveyCollectRequest(
        @NotEmpty(message = "응답 목록이 비어 있습니다.")
        List<String> artists,

        /** 검색 스토어프론트. 국내 가수 위주면 kr. 미지정 시 kr. */
        String storefront,

        /** 가수당 가져올 곡 수. 미지정 시 설정값. */
        Integer songsPerArtist
) {}
