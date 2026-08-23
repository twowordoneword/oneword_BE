package com.example.musing_BE.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock kstClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
