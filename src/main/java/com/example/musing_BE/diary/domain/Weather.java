package com.example.musing_BE.diary.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 날씨 5종 고정값. DB·API 모두 한글 코드로 주고받는다(ADR-004). */
public enum Weather {
    SUNNY("맑음"), CLOUDY("흐림"), RAIN("비"), SNOW("눈"), WIND("바람");

    private final String code;
    Weather(String code) { this.code = code; }

    @JsonValue
    public String getCode() { return code; }

    @JsonCreator
    public static Weather from(String value) {
        return Arrays.stream(values())
                .filter(w -> w.code.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("잘못된 weather 값: " + value));
    }
}
