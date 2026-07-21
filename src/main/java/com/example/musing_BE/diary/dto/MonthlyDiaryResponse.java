package com.example.musing_BE.diary.dto;

import com.example.musing_BE.diary.domain.Mood;
import com.example.musing_BE.diary.entity.Diary;
import com.example.musing_BE.track.entity.Track;
import java.time.LocalDate;
import java.util.List;

/** 캘린더용 월별 기록. API_SPEC 2.1. 기록 있는 날만 반환. */
public record MonthlyDiaryResponse(
        String month,
        List<DayItem> days
) {
    public record DayItem(
            LocalDate date,
            boolean hasDiary,
            boolean hasMusic,
            String coverArtworkUrl,
            Mood mood
    ) {
        public static DayItem from(Diary d) {
            Track cover = d.getMyTrack() != null ? d.getMyTrack() : d.getTodayTrack();
            String url = cover != null ? cover.getArtworkUrl() : null;
            return new DayItem(d.getDiaryDate(), true, d.hasMusic(), url, d.getMood());
        }
    }

    public static MonthlyDiaryResponse of(String month, List<Diary> diaries) {
        return new MonthlyDiaryResponse(month, diaries.stream().map(DayItem::from).toList());
    }
}
