package com.example.musing_BE.track.service;

import com.example.musing_BE.track.client.ItunesClient;
import com.example.musing_BE.track.dto.TrackInfoResponse;
import com.example.musing_BE.track.dto.TrackSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackSearchService {

    private final ItunesClient itunesClient;

    public TrackSearchResponse search(String query, int limit) {
        List<TrackInfoResponse> results = itunesClient.searchSongs(query, limit, "KR");
        return new TrackSearchResponse(query, results);
    }
}
