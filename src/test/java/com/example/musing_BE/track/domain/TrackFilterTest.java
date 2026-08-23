package com.example.musing_BE.track.domain;

import com.example.musing_BE.track.dto.CollectedTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrackFilter — 후보로 쓸 곡 고르기")
class TrackFilterTest {

    private CollectedTrack track(String name, String album, String preview) {
        return new CollectedTrack(1L, 2L, name, "아티스트", album, "art", preview, "K-Pop");
    }

    @Test
    @DisplayName("미리듣기가 없으면 제외한다")
    void requiresPreview() {
        assertThat(TrackFilter.isUsable(track("정상곡", "앨범", "preview"))).isTrue();
        assertThat(TrackFilter.isUsable(track("정상곡", "앨범", null))).isFalse();
        assertThat(TrackFilter.isUsable(track("정상곡", "앨범", "   "))).isFalse();
    }

    @Test
    @DisplayName("Live·Instrumental 등 파생 버전은 제외한다")
    void excludesDerivedVersions() {
        assertThat(TrackFilter.isUsable(track("외사랑 (Live)", "앨범", "p"))).isFalse();
        assertThat(TrackFilter.isUsable(track("자장가 II (Instrumental)", "앨범", "p"))).isFalse();
        assertThat(TrackFilter.isUsable(track("좋은 날 (Remix)", "앨범", "p"))).isFalse();
        assertThat(TrackFilter.isUsable(track("밤편지 (Remastered 2020)", "앨범", "p"))).isFalse();
        assertThat(TrackFilter.isUsable(track("사랑이라는 이유로 (Inst.)", "앨범", "p"))).isFalse();
    }

    @Test
    @DisplayName("원곡은 통과한다")
    void keepsOriginals() {
        assertThat(TrackFilter.isUsable(track("밤편지", "앨범", "p"))).isTrue();
        assertThat(TrackFilter.isUsable(track("서른 즈음에", "앨범", "p"))).isTrue();
        assertThat(TrackFilter.isUsable(track("어느 60대 노부부 이야기", "앨범", "p"))).isTrue();
    }

    @Test
    @DisplayName("DJ Mix 모음집은 제외한다")
    void excludesDjMixAlbum() {
        assertThat(TrackFilter.isUsable(track("FaSHioN (Mixed)", "INS LAND: DJ Mix", "p"))).isFalse();
    }

    @Test
    @DisplayName("이름이나 아티스트가 없으면 제외한다")
    void requiresNameAndArtist() {
        assertThat(TrackFilter.isUsable(new CollectedTrack(1L, 2L, null, "가수", "앨범", "a", "p", "Pop"))).isFalse();
        assertThat(TrackFilter.isUsable(new CollectedTrack(1L, 2L, "곡", null, "앨범", "a", "p", "Pop"))).isFalse();
        assertThat(TrackFilter.isUsable(null)).isFalse();
    }
}
