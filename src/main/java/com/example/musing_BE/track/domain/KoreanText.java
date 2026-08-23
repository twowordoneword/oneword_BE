package com.example.musing_BE.track.domain;

/**
 * 한글 포함 여부 판별.
 * Apple kr 스토어프론트는 국내 아티스트를 한글 표기로 주므로(코르티스·제니 vs Post Malone),
 * 아티스트명에 한글이 있으면 한국 곡으로 본다 (RECOMMENDATION_STAGE2 §5.3).
 */
public final class KoreanText {

    private KoreanText() {}

    /** 완성형 한글(가~힣) 포함 여부. */
    public static boolean containsHangul(String s) {
        if (s == null || s.isBlank()) return false;
        return s.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
    }
}
