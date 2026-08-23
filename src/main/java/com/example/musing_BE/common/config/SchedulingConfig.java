package com.example.musing_BE.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 배치 활성화 (차트 수집 등). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
