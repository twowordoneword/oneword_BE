package com.example.musing_BE.recommendation.domain;

import org.springframework.stereotype.Component;

/**
 * 같은 조건이면 같은 곡을 고르게 하는 선택기 (RECOMMENDATION_STAGE2 §5.4).
 *
 * <p>난수를 쓰면 새로고침할 때마다 곡이 바뀌어 "오늘의 곡"이 되지 못한다.
 * 그렇다고 결과를 DB에 저장하면 추천 API가 상태를 갖게 된다.
 * 그래서 <b>입력값으로 시드를 만들어</b> 항상 같은 자리를 고른다.
 *
 * <ul>
 *   <li>같은 사람 · 같은 날 · 같은 기분/날씨 → 항상 같은 곡</li>
 *   <li>기분이나 날씨를 바꾸면 → 다른 곡 (조건이 달라졌으므로 자연스럽다)</li>
 * </ul>
 */
@Component
public class DeterministicPicker {

    // FNV-1a 64비트 상수
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * @return 0 이상 size 미만의 인덱스. size가 0 이하면 -1.
     */
    public int pickIndex(String seedKey, int size) {
        if (size <= 0) return -1;
        long hash = fnv1a64(seedKey);
        return (int) Math.floorMod(hash, (long) size);
    }

    /**
     * 문자열 → 64비트 해시.
     * <p>{@code Objects.hash()}를 쓰지 않는 이유: enum의 hashCode는 객체 주소 기반이라
     * <b>JVM을 재시작하면 값이 달라진다.</b> 그러면 서버를 껐다 켤 때마다 같은 조건인데
     * 다른 곡이 나온다. FNV-1a는 입력이 같으면 언제 어디서나 같은 값을 낸다.
     */
    private long fnv1a64(String s) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
