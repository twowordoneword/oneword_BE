package com.example.musing_BE.track.domain;

import com.example.musing_BE.track.dto.CollectedTrack;

/**
 * 후보로 쓸 곡인지 판정 (RECOMMENDATION_STAGE2 §3.5).
 * <p>차트 수집과 설문 수집이 같은 규칙을 써야 해서 한곳에 모았다.
 */
public final class TrackFilter {

    private TrackFilter() {}

    /** 앨범명에 들어가면 제외 — 클럽 믹스 모음집 등. */
    private static final String[] EXCLUDED_ALBUM_KEYWORDS = {"DJ Mix"};

    /**
     * 곡명에 들어가면 제외.
     * <p>원곡이 아닌 파생 버전은 "오늘의 곡"으로 어울리지 않고, 같은 곡이 여러 버전으로
     * 후보에 쌓여 다양성을 갉아먹는다. (실측: 김광석 카탈로그에 Live·Instrumental이 다수)
     */
    private static final String[] EXCLUDED_TRACK_KEYWORDS = {
            "(live", "(inst", "(remix", "(remaster", "(karaoke", "(mr)", "(acoustic ver"
    };

    /**
     * 후보 자격 판정.
     * <p><b>미리듣기가 없으면 무조건 제외</b> — 감정값 분석도, 앱 내 재생도 불가능하다.
     */
    public static boolean isUsable(CollectedTrack c) {
        if (c == null) return false;
        if (c.name() == null || c.artist() == null) return false;
        if (c.previewUrl() == null || c.previewUrl().isBlank()) return false;

        String album = c.album() == null ? "" : c.album();
        for (String keyword : EXCLUDED_ALBUM_KEYWORDS) {
            if (album.contains(keyword)) return false;
        }

        String name = c.name().toLowerCase();
        for (String keyword : EXCLUDED_TRACK_KEYWORDS) {
            if (name.contains(keyword)) return false;
        }
        return true;
    }
}
