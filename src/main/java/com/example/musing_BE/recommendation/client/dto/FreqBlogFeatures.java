package com.example.musing_BE.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * FreqBlog /lookup 응답 중 우리가 쓰는 필드만.
 * <p>실제 응답은 43개 필드지만 {@code @JsonIgnoreProperties}로 나머지는 무시한다.
 * <p>{@code energy}가 우리 좌표계의 arousal(활기)에 대응한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FreqBlogFeatures(
        String track_name,
        String artist_name,
        BigDecimal valence,
        BigDecimal energy,
        BigDecimal acousticness,
        BigDecimal bpm
) {
    /** 감정 좌표로 쓸 수 있는 응답인지. */
    public boolean usable() {
        return valence != null && energy != null;
    }
}
