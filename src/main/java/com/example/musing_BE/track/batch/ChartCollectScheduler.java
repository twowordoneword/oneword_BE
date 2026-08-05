package com.example.musing_BE.track.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 후보 풀 수집 자동 실행 (기본: 매주 월요일 03:00).
 * ⚠️ 단일 인스턴스 전제 — 서버를 여러 대로 늘리면 중복 실행 방지(락)가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartCollectScheduler {

    private final ChartCollectService chartCollectService;

    /** 자동 실행 스위치. 설문 기반 수집을 먼저 하기로 해 기본은 꺼 둔다(수동 트리거는 계속 가능). */
    @Value("${musing.chart.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${musing.chart.cron:0 0 3 * * MON}", zone = "Asia/Seoul")
    public void run() {
        if (!enabled) {
            log.debug("차트 자동 수집이 꺼져 있어 건너뜀 (musing.chart.enabled=false)");
            return;
        }
        log.info("주간 차트 수집 배치 시작");
        try {
            chartCollectService.collect();
        } catch (Exception e) {
            log.error("차트 수집 배치 실패: {}", e.getMessage(), e);
        }
    }
}
