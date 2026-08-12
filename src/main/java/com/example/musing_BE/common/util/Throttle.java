package com.example.musing_BE.common.util;

/**
 * 외부 API 호출 사이에 간격을 둔다.
 *
 * <p>같은 코드가 수집 서비스 두 곳과 감정값 배치에 각각 복사돼 있었다.
 * (iTunes는 분당 약 20콜, FreqBlog은 동시 6건 제한이 있어 배치가 몰아치면 차단당한다)
 */
public final class Throttle {

    private Throttle() {}

    /**
     * 지정 시간만큼 멈춘다. 0 이하면 아무것도 하지 않는다.
     *
     * <p>{@code InterruptedException}을 잡으면 자바가 스레드의 "중단 요청됨" 표시를 꺼버린다.
     * 그대로 두면 바깥 코드가 중단 사실을 알 수 없으므로 다시 켜 준다.
     */
    public static void pause(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배치가 중단되었습니다.", e);
        }
    }
}
