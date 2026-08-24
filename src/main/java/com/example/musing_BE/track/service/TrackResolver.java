package com.example.musing_BE.track.service;

import com.example.musing_BE.diary.dto.TrackDto;
import com.example.musing_BE.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일기에서 들어온 곡을 곡 마스터(tracks)에 확보한다.
 *
 * <p><b>{@code REQUIRES_NEW}인 이유:</b> 조회와 저장 사이에 다른 요청이 같은 곡을 넣으면
 * {@code uq_tracks_name_artist}에 걸린다. 제약 위반이 난 트랜잭션은 롤백 표시가 찍혀
 * 그 안에서는 재조회조차 할 수 없다. 호출부(일기 트랜잭션)와 분리해 두어야
 * 곡 저장이 한 번 실패해도 일기 트랜잭션까지 함께 죽지 않고 다시 시도할 수 있다.
 *
 * <p>배치 경로의 {@code TrackUpsertService}와 목적이 같지만, 그쪽은 수집 DTO를 받아
 * 장르·유입경로까지 채우므로 합치지 않았다.
 */
@Service
@RequiredArgsConstructor
public class TrackResolver {

    private final TrackRepository trackRepository;

    /**
     * 이미 있으면 메타(앨범·커버·미리듣기)를 갱신해 재사용하고, 없으면 새로 넣는다.
     *
     * @return 곡 마스터 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long resolveId(TrackDto dto) {
        return trackRepository.findByNameAndArtist(dto.name(), dto.artist())
                .map(existing -> {
                    existing.updateMeta(dto.album(), dto.artworkUrl(), dto.previewUrl());
                    return existing.getId();
                })
                // saveAndFlush로 INSERT를 지금 보낸다. 커밋까지 미루면 제약 위반이 호출부가 아니라
                // 트랜잭션 커밋 시점에 터져 재시도 지점을 잡기 어렵다.
                .orElseGet(() -> trackRepository.saveAndFlush(dto.toNewEntity()).getId());
    }
}
