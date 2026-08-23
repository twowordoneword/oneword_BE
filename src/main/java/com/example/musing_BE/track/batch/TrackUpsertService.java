package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.domain.KoreanText;
import com.example.musing_BE.track.domain.TrackOrigin;
import com.example.musing_BE.track.dto.CollectedTrack;
import com.example.musing_BE.track.entity.Track;
import com.example.musing_BE.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집한 곡을 tracks에 반영한다.
 * <p>수집 서비스와 분리한 이유: 같은 클래스 안에서 호출하면 {@code @Transactional}이 적용되지 않는다
 * (Spring AOP 자체 호출 문제). 곡 단위로 트랜잭션을 끊어 한 곡의 실패가 배치 전체를 되돌리지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class TrackUpsertService {

    private final TrackRepository trackRepository;

    /**
     * 이미 있으면 비어 있는 칸만 채우고, 없으면 새로 넣는다.
     *
     * @return 새로 넣었으면 true
     */
    @Transactional
    public boolean upsert(CollectedTrack c, TrackOrigin origin) {
        boolean korean = KoreanText.containsHangul(c.artist());
        return trackRepository.findByNameAndArtist(c.name(), c.artist())
                .map(existing -> {
                    existing.updateMeta(c.album(), c.artworkUrl(), c.previewUrl());
                    existing.fillCollected(c.genre(), korean, origin);
                    return false;
                })
                .orElseGet(() -> {
                    trackRepository.save(Track.builder()
                            .name(c.name())
                            .artist(c.artist())
                            .album(c.album())
                            .artworkUrl(c.artworkUrl())
                            .previewUrl(c.previewUrl())
                            .genre(c.genre())
                            .isKorean(korean)
                            .origin(origin)
                            .build());
                    return true;
                });
    }
}
