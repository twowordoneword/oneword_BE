package com.example.musing_BE.track.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KoreanText — 한국 곡 판별")
class KoreanTextTest {

    @Test
    @DisplayName("한글이 있으면 true")
    void hangul() {
        assertThat(KoreanText.containsHangul("아이유")).isTrue();
        assertThat(KoreanText.containsHangul("코르티스")).isTrue();
        assertThat(KoreanText.containsHangul("015B")).isFalse();       // 숫자+영문 그룹명
        assertThat(KoreanText.containsHangul("잔나비 (feat. 아무개)")).isTrue();
    }

    @Test
    @DisplayName("영문 아티스트는 false")
    void nonHangul() {
        assertThat(KoreanText.containsHangul("Post Malone")).isFalse();
        assertThat(KoreanText.containsHangul("Ariana Grande")).isFalse();
        assertThat(KoreanText.containsHangul("BTS")).isFalse();        // 영문 표기는 판별 불가 — 알려진 한계
    }

    @Test
    @DisplayName("null·공백은 false")
    void blank() {
        assertThat(KoreanText.containsHangul(null)).isFalse();
        assertThat(KoreanText.containsHangul("")).isFalse();
        assertThat(KoreanText.containsHangul("   ")).isFalse();
    }
}
