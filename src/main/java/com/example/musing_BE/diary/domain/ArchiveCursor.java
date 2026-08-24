package com.example.musing_BE.diary.domain;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 곡 아카이브 커서.
 *
 * <p>정렬 기준이 일기 날짜 내림차순인데, <b>같은 날짜에 곡이 두 개(MY·RECOMMENDED) 있을 수 있어</b>
 * 날짜만으로는 어디까지 읽었는지 특정할 수 없다. 그래서 날짜와 diary_track id를 함께 담는다.
 *
 * <p>형식은 {@code yyyy-MM-dd:id}. 클라이언트는 이 값을 해석하지 말고 받은 그대로 다시 넘기면 된다.
 */
public record ArchiveCursor(LocalDate date, Long diaryTrackId) {

    public static ArchiveCursor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;   // 첫 페이지
        }
        int separator = raw.lastIndexOf(':');
        if (separator < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return new ArchiveCursor(
                    LocalDate.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1))
            );
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public String format() {
        return date + ":" + diaryTrackId;
    }
}
