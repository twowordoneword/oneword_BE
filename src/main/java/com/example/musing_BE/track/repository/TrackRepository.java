package com.example.musing_BE.track.repository;

import com.example.musing_BE.track.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {
    Optional<Track> findByIsrc(String isrc);
    Optional<Track> findByNameAndArtist(String name, String artist);
}
