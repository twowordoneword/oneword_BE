package com.example.musing_BE.stats.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 홈 화면 통계.
 * monthlyCount는 month 파라미터가 있을 때만 채워지고, 없으면 응답에서 생략된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsResponse(
        long totalCount,     // 전체 일기 수 (No.XX 표시용)
        Long monthlyCount,   // 이번 달 일기 수 (month 파라미터 있을 때만)
        long musicCount      // 곡이 연결된 일기 수
) {}
