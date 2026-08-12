package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectedTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackCollector — 저장·재시도·실패 격리")
class TrackCollectorTest {

    @Mock TrackUpsertService trackUpsertService;
    @InjectMocks TrackCollector collector;

    private CollectedTrack track(String name, String preview) {
        return new CollectedTrack(1L, 2L, name, "가수", "앨범", "art", preview, "K-Pop");
    }

    @Test
    @DisplayName("쓸 수 없는 곡은 저장하지 않고 skipped로 센다")
    void skipsUnusable() {
        given(trackUpsertService.upsert(any(), any())).willReturn(true);

        TrackCollector.Counts c = collector.upsertAll(List.of(
                track("정상곡", "preview"),
                track("미리듣기없음", null),
                track("외사랑 (Live)", "preview")), TrackOrigin.CHART);

        assertThat(c.inserted()).isEqualTo(1);
        assertThat(c.skipped()).isEqualTo(2);
        verify(trackUpsertService, times(1)).upsert(any(), any());
    }

    @Test
    @DisplayName("신규와 갱신을 나눠 센다")
    void countsInsertedAndUpdated() {
        given(trackUpsertService.upsert(any(), any())).willReturn(true, false);

        TrackCollector.Counts c = collector.upsertAll(List.of(
                track("새 곡", "p"), track("있던 곡", "p")), TrackOrigin.CHART);

        assertThat(c.inserted()).isEqualTo(1);
        assertThat(c.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 삽입으로 유니크 제약에 걸리면 한 번 다시 시도한다")
    void retriesOnConstraintViolation() {
        // 첫 시도는 충돌(다른 작업이 먼저 넣음), 재시도는 갱신 경로로 성공
        given(trackUpsertService.upsert(any(), any()))
                .willThrow(new DataIntegrityViolationException("uq_tracks_name_artist"))
                .willReturn(false);

        TrackCollector.Counts c = collector.upsertAll(List.of(track("밤편지", "p")), TrackOrigin.SURVEY);

        assertThat(c.updated()).isEqualTo(1);
        assertThat(c.skipped()).isZero();
        verify(trackUpsertService, times(2)).upsert(any(), any());
    }

    @Test
    @DisplayName("재시도까지 실패하면 그 곡만 건너뛴다")
    void skipsWhenRetryAlsoFails() {
        given(trackUpsertService.upsert(any(), any()))
                .willThrow(new DataIntegrityViolationException("1차"))
                .willThrow(new DataIntegrityViolationException("2차"));

        TrackCollector.Counts c = collector.upsertAll(List.of(track("밤편지", "p")), TrackOrigin.SURVEY);

        assertThat(c.skipped()).isEqualTo(1);
        assertThat(c.inserted()).isZero();
    }

    @Test
    @DisplayName("한 곡이 실패해도 나머지는 계속 저장한다")
    void oneFailureDoesNotStopBatch() {
        // 유니크 제약 외의 예외는 재시도하지 않고 그 곡만 건너뛴다
        given(trackUpsertService.upsert(any(), any()))
                .willReturn(true)
                .willThrow(new RuntimeException("DB 오류"))
                .willReturn(true);

        TrackCollector.Counts c = collector.upsertAll(List.of(
                track("곡1", "p"), track("곡2", "p"), track("곡3", "p")), TrackOrigin.CHART);

        assertThat(c.inserted()).isEqualTo(2);   // 1번·3번은 저장됨
        assertThat(c.skipped()).isEqualTo(1);    // 2번만 건너뜀
    }
}
