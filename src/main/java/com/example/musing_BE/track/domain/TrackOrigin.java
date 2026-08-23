package com.example.musing_BE.track.domain;

/**
 * 곡이 tracks에 들어온 경로 (RECOMMENDATION_STAGE2 §3.0).
 * 현재는 모두 추천 후보로 쓰지만, 나중에 "차트 곡만" 처럼 좁힐 수 있도록 구분해 둔다.
 */
public enum TrackOrigin {
    /** 사용자가 일기에 붙임 */
    USER,
    /** 차트 수집 배치가 넣음 */
    CHART,
    /** 설문으로 모은 가수의 곡 */
    SURVEY
}
