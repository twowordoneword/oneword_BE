package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.domain.TrackFilter;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectedTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 수집한 곡들을 걸러 저장한다. 차트·설문 수집이 공유한다.
 *
 * <p><b>왜 별도 빈인가</b> — 저장은 {@link TrackUpsertService}가 곡 단위 트랜잭션으로 처리하는데,
 * 재시도하려면 <b>새 트랜잭션</b>이 필요하다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아
 * 트랜잭션이 새로 열리지 않으므로, 호출하는 쪽을 다른 빈으로 분리했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackCollector {

    private final TrackUpsertService trackUpsertService;

    /**
     * 후보를 걸러 저장한다. <b>한 곡의 실패가 배치 전체를 멈추지 않는다.</b>
     */
    public Counts upsertAll(List<CollectedTrack> candidates, TrackOrigin origin) {
        int skipped = 0;
        int inserted = 0;
        int updated = 0;

        for (CollectedTrack c : candidates) {
            if (!TrackFilter.isUsable(c)) {
                skipped++;
                continue;
            }
            try {
                if (upsertWithRetry(c, origin)) inserted++;
                else updated++;
            } catch (Exception e) {
                skipped++;
                log.warn("곡 저장 실패 — 건너뜀 ({} - {}): {}", c.artist(), c.name(), e.getMessage());
            }
        }
        return new Counts(skipped, inserted, updated);
    }

    /**
     * 조회 후 저장 사이에 다른 작업이 같은 곡을 넣으면 유니크 제약(uq_tracks_name_artist)에 걸린다.
     * 그때는 이미 행이 생긴 뒤이므로, 다시 시도하면 갱신 경로로 처리된다.
     *
     * <p>제약 위반이 난 트랜잭션은 롤백 표시가 찍혀 재사용할 수 없다.
     * 여기서 다시 호출하면 프록시를 거쳐 <b>새 트랜잭션</b>이 열리므로 안전하다.
     */
    private boolean upsertWithRetry(CollectedTrack c, TrackOrigin origin) {
        try {
            return trackUpsertService.upsert(c, origin);
        } catch (DataIntegrityViolationException e) {
            log.debug("동시 삽입 충돌 — 재시도 ({} - {})", c.artist(), c.name());
            return trackUpsertService.upsert(c, origin);
        }
    }

    /** 저장 결과 집계. */
    public record Counts(int skipped, int inserted, int updated) {}
}
