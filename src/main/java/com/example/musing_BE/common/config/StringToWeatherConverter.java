package com.example.musing_BE.common.config;

import com.example.musing_BE.diary.domain.Weather;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** 쿼리 파라미터의 한글 문자열(예: "바람")을 Weather enum으로 변환. 잘못된 값이면 400. */
@Component
public class StringToWeatherConverter implements Converter<String, Weather> {
    @Override
    public Weather convert(String source) {
        return Weather.from(source);
    }
}
