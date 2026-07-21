package com.example.musing_BE.diary.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 기분 8종 고정값. DB·API 모두 한글 코드(code)로 주고받는다(ADR-004). */
public enum Mood {
    JOY("기쁨"), GLOOM("우울"), SAD("슬픔"), ANGRY("화남"),
    CALM("평온"), NORMAL("보통"), STUFFY("답답"), UNKNOWN("모름");

    private final String code;
    Mood(String code) { this.code = code; }

    @JsonValue
    public String getCode() { return code; }

    @JsonCreator
    public static Mood from(String value) {
        return Arrays.stream(values())
                .filter(m -> m.code.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("잘못된 mood 값: " + value));
    }
}
