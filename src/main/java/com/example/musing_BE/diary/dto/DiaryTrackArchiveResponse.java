package com.example.musing_BE.diary.dto;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.domain.TrackRole;
import com.example.musing_BE.diary.entity.DiaryTrack;
import com.example.musing_BE.track.entity.Track;

import java.time.LocalDate;
import java.util.List;

/**
 * 음악 아카이브 — 기록에 담긴 곡을 최신순으로 모아 본다.
 *
 * <p>월별 조회(2.1)는 커버 URL만 주기 때문에, 아카이브를 만들려면 날짜마다 상세를 따로 불러야 했다.
 * 1년치면 요청이 수백 개가 되므로 전용 목록을 둔다.
 *
 * @param nextCursor 다음 페이지 커서. 마지막 페이지면 {@code null}
 */
public record DiaryTrackArchiveResponse(
        List<Item> items,
        String nextCursor
) {
    /** 일기 하나에 MY·RECOMMENDED가 모두 있으면 같은 날짜가 두 번 나온다. */
    public record Item(
            LocalDate date,
            String name,
            String artist,
            String album,
            String artworkUrl,
            String previewUrl,
            TrackRole role,
            Mood mood
    ) {
        public static Item from(DiaryTrack diaryTrack) {
            Track track = diaryTrack.getTrack();
            return new Item(
                    diaryTrack.getDiary().getDiaryDate(),
                    track.getName(),
                    track.getArtist(),
                    track.getAlbum(),
                    track.getArtworkUrl(),
                    track.getPreviewUrl(),
                    diaryTrack.getRole(),
                    diaryTrack.getDiary().getMood()
            );
        }
    }
}
