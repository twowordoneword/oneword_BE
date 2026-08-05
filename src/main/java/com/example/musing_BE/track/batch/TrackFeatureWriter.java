package com.example.musing_BE.track.batch;

import com.example.musing_BE.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 감정값을 tracks에 기록한다.
 * <p>수집 서비스와 분리한 이유는 {@link TrackUpsertService}와 같다 —
 * 같은 클래스 안에서 부르면 {@code @Transactional}이 적용되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TrackFeatureWriter {

    private final TrackRepository trackRepository;

    @Transactional
    public void write(Long trackId, BigDecimal valence, BigDecimal energy,
                      BigDecimal acousticness, BigDecimal tempo, String source) {
        trackRepository.findById(trackId)
                .ifPresent(t -> t.updateFeatures(valence, energy, acousticness, tempo, source));
    }
}
