package com.example.musing_BE.recommendation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeterministicPicker — 같은 조건이면 같은 곡")
class DeterministicPickerTest {

    private final DeterministicPicker picker = new DeterministicPicker();

    @Test
    @DisplayName("같은 키로 여러 번 호출해도 같은 인덱스")
    void sameKeySameIndex() {
        String key = "1|2026-08-05|CALM|WIND";

        int first = picker.pickIndex(key, 30);

        for (int i = 0; i < 100; i++) {
            assertThat(picker.pickIndex(key, 30)).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("날짜·기분·날씨 중 하나만 달라져도 다른 인덱스가 나올 수 있다")
    void differentKeyDifferentIndex() {
        int base = picker.pickIndex("1|2026-08-05|CALM|WIND", 30);
        int otherDate = picker.pickIndex("1|2026-08-06|CALM|WIND", 30);
        int otherMood = picker.pickIndex("1|2026-08-05|JOY|WIND", 30);
        int otherWeather = picker.pickIndex("1|2026-08-05|CALM|RAIN", 30);

        // 우연히 같을 수도 있으므로 "전부 같지는 않다"로 확인한다.
        // Set.of(...)는 중복 원소가 있으면 예외를 던지므로 HashSet을 쓴다.
        Set<Integer> distinct = new HashSet<>(List.of(base, otherDate, otherMood, otherWeather));
        assertThat(distinct).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("인덱스는 항상 0 이상 size 미만")
    void indexInRange() {
        for (int size = 1; size <= 50; size++) {
            for (int day = 1; day <= 28; day++) {
                int idx = picker.pickIndex("1|2026-08-%02d|SAD|RAIN".formatted(day), size);
                assertThat(idx).isBetween(0, size - 1);
            }
        }
    }

    @Test
    @DisplayName("후보가 없으면 -1")
    void emptyPool() {
        assertThat(picker.pickIndex("any", 0)).isEqualTo(-1);
        assertThat(picker.pickIndex("any", -3)).isEqualTo(-1);
    }

    @Test
    @DisplayName("한 달간 같은 조건이어도 곡이 골고루 흩어진다")
    void spreadsAcrossMonth() {
        Set<Integer> picked = new HashSet<>();
        for (int day = 1; day <= 30; day++) {
            picked.add(picker.pickIndex("1|2026-08-%02d|CALM|WIND".formatted(day), 30));
        }
        // 해시 특성상 일부 중복은 자연스럽다. 절반 이상 다르면 충분히 흩어진 것으로 본다.
        assertThat(picked.size()).isGreaterThanOrEqualTo(15);
    }
}
