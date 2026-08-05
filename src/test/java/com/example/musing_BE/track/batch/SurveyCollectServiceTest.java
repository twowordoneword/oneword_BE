package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectResult;
import com.example.musing_BE.track.dto.CollectedTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SurveyCollectService — 설문 가수 → 곡 수집")
class SurveyCollectServiceTest {

    @Mock ItunesClient itunesClient;
    @Mock TrackUpsertService trackUpsertService;

    SurveyCollectService service;

    @BeforeEach
    void setUp() {
        service = new SurveyCollectService(itunesClient, trackUpsertService);
        ReflectionTestUtils.setField(service, "defaultSongsPerArtist", 10);
        ReflectionTestUtils.setField(service, "requestDelayMs", 0L);   // 테스트에서는 대기 없음
    }

    private CollectedTrack track(String name, String artist, String preview, String album) {
        return new CollectedTrack(1L, 2L, name, artist, album, "art", preview, "K-Pop");
    }

    @Test
    @DisplayName("같은 가수가 여러 번 들어와도 한 번만 조회한다")
    void deduplicatesArtists() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "아이유", "K-Pop")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString())).willReturn(List.of());

        service.collect(List.of("아이유", "아이유", " 아이유 ", "아이유"), "kr", 10);

        verify(itunesClient, times(1)).searchArtist(eq("아이유"), anyString());
    }

    @Test
    @DisplayName("빈 이름은 건너뛴다")
    void skipsBlankNames() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "넬", "Rock")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString())).willReturn(List.of());

        service.collect(java.util.Arrays.asList("넬", "", "   ", null), "kr", 10);

        verify(itunesClient, times(1)).searchArtist(anyString(), anyString());
    }

    @Test
    @DisplayName("가수를 못 찾아도 나머지 가수는 계속 처리한다")
    void continuesWhenArtistNotFound() {
        given(itunesClient.searchArtist(eq("없는가수"), anyString())).willReturn(Optional.empty());
        given(itunesClient.searchArtist(eq("아이유"), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "아이유", "K-Pop")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString()))
                .willReturn(List.of(track("밤편지", "아이유", "preview", "앨범")));
        given(trackUpsertService.upsert(any(), any())).willReturn(true);

        CollectResult result = service.collect(List.of("없는가수", "아이유"), "kr", 10);

        assertThat(result.artists()).isEqualTo(1);      // 확인된 가수만 센다
        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("미리듣기가 없는 곡은 저장하지 않는다")
    void skipsTracksWithoutPreview() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "아이유", "K-Pop")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString())).willReturn(List.of(
                track("정상곡", "아이유", "preview", "앨범"),
                track("미리듣기없음", "아이유", null, "앨범"),
                track("빈문자열", "아이유", "  ", "앨범")));
        given(trackUpsertService.upsert(any(), any())).willReturn(true);

        CollectResult result = service.collect(List.of("아이유"), "kr", 10);

        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        verify(trackUpsertService, times(1)).upsert(any(), eq(TrackOrigin.SURVEY));
    }

    @Test
    @DisplayName("DJ Mix 음원은 제외한다")
    void skipsDjMix() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "아이유", "K-Pop")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString())).willReturn(List.of(
                track("정상곡", "아이유", "preview", "정규앨범"),
                track("믹스곡", "아이유", "preview", "INS LAND: DJ Mix")));
        given(trackUpsertService.upsert(any(), any())).willReturn(true);

        CollectResult result = service.collect(List.of("아이유"), "kr", 10);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("origin은 항상 SURVEY로 저장된다")
    void savesWithSurveyOrigin() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "김광석", "발라드")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString()))
                .willReturn(List.of(track("서른 즈음에", "김광석", "preview", "앨범")));
        given(trackUpsertService.upsert(any(), any())).willReturn(true);

        service.collect(List.of("김광석"), "kr", 10);

        ArgumentCaptor<TrackOrigin> origin = ArgumentCaptor.forClass(TrackOrigin.class);
        verify(trackUpsertService).upsert(any(), origin.capture());
        assertThat(origin.getValue()).isEqualTo(TrackOrigin.SURVEY);
    }

    @Test
    @DisplayName("songsPerArtist가 없으면 설정 기본값을 쓴다")
    void usesDefaultSongsPerArtist() {
        given(itunesClient.searchArtist(anyString(), anyString()))
                .willReturn(Optional.of(new ItunesClient.ItunesArtist(100L, "아이유", "K-Pop")));
        given(itunesClient.lookupArtistSongs(anyLong(), anyInt(), anyString())).willReturn(List.of());

        service.collect(List.of("아이유"), null, null);

        verify(itunesClient).lookupArtistSongs(eq(100L), eq(10), eq("kr"));   // 기본값 10, 기본 스토어 kr
    }
}
