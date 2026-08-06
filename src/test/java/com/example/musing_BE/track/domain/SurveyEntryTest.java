package com.example.musing_BE.track.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SurveyEntry — 설문 응답 한 줄 파싱")
class SurveyEntryTest {

    @Test
    @DisplayName("'가수 - 곡명' 을 나눈다")
    void parsesArtistAndTrack() {
        SurveyEntry e = SurveyEntry.parse("아이유 - 밤편지");

        assertThat(e.artist()).isEqualTo("아이유");
        assertThat(e.track()).isEqualTo("밤편지");
        assertThat(e.hasTrack()).isTrue();
    }

    @Test
    @DisplayName("공백 없는 하이픈도 처리한다")
    void parsesWithoutSpaces() {
        SurveyEntry e = SurveyEntry.parse("아이유-밤편지");

        assertThat(e.artist()).isEqualTo("아이유");
        assertThat(e.track()).isEqualTo("밤편지");
    }

    @Test
    @DisplayName("여러 구분자를 지원한다 (–, —, /)")
    void supportsVariousSeparators() {
        assertThat(SurveyEntry.parse("넬 – 기억을 걷는 시간").track()).isEqualTo("기억을 걷는 시간");
        assertThat(SurveyEntry.parse("넬 — 기억을 걷는 시간").track()).isEqualTo("기억을 걷는 시간");
        assertThat(SurveyEntry.parse("넬 / 기억을 걷는 시간").track()).isEqualTo("기억을 걷는 시간");
    }

    @Test
    @DisplayName("가수 이름만 있으면 곡은 null")
    void artistOnly() {
        SurveyEntry e = SurveyEntry.parse("김광석");

        assertThat(e.artist()).isEqualTo("김광석");
        assertThat(e.track()).isNull();
        assertThat(e.hasTrack()).isFalse();
    }

    @Test
    @DisplayName("곡명에 하이픈이 있어도 첫 구분자에서만 나눈다")
    void splitsAtFirstSeparatorOnly() {
        SurveyEntry e = SurveyEntry.parse("잔나비 - 뜨거운 여름밤은 가고 - 남은 건 볼품없지만");

        assertThat(e.artist()).isEqualTo("잔나비");
        assertThat(e.track()).isEqualTo("뜨거운 여름밤은 가고 - 남은 건 볼품없지만");
    }

    @Test
    @DisplayName("앞뒤 공백을 정리한다")
    void trimsWhitespace() {
        SurveyEntry e = SurveyEntry.parse("  아이유  -  밤편지  ");

        assertThat(e.artist()).isEqualTo("아이유");
        assertThat(e.track()).isEqualTo("밤편지");
    }

    @Test
    @DisplayName("null·빈 문자열은 null을 돌려준다")
    void blankReturnsNull() {
        assertThat(SurveyEntry.parse(null)).isNull();
        assertThat(SurveyEntry.parse("")).isNull();
        assertThat(SurveyEntry.parse("   ")).isNull();
    }

    @Test
    @DisplayName("검색어는 곡이 있으면 '가수 곡명', 없으면 가수만")
    void searchTerm() {
        assertThat(SurveyEntry.parse("김광석 - 서른 즈음에").searchTerm()).isEqualTo("김광석 서른 즈음에");
        assertThat(SurveyEntry.parse("김광석").searchTerm()).isEqualTo("김광석");
    }

    @Test
    @DisplayName("구분자로 시작하면 전체를 가수명으로 본다 (검색 단계에서 걸러짐)")
    void leadingSeparatorIsTreatedAsArtist() {
        SurveyEntry e = SurveyEntry.parse("- 밤편지");

        assertThat(e).isNotNull();
        assertThat(e.artist()).isEqualTo("- 밤편지");
        assertThat(e.hasTrack()).isFalse();
        // 이런 응답은 iTunes 검색에서 못 찾아 notFound로 집계되고 배치는 계속 돈다
    }

    @Test
    @DisplayName("⚠️ 이름에 하이픈이 있는 가수는 잘못 나뉜다 (알려진 한계)")
    void hyphenatedArtistNameIsSplit() {
        SurveyEntry e = SurveyEntry.parse("Jay-Z");

        assertThat(e.artist()).isEqualTo("Jay");
        assertThat(e.track()).isEqualTo("Z");
        // 곡 검색이 실패하면 이름("Jay")으로 폴백하므로 대개 찾아진다.
        // 국내 설문에서는 드문 경우라 감수하고, 문제가 되면 구분자 규칙을 좁힌다.
    }
}
