package com.example.musing_BE.common.config;

import com.example.musing_BE.diary.domain.Mood;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** 쿼리 파라미터의 한글 문자열(예: "평온")을 Mood enum으로 변환. 잘못된 값이면 400. */
@Component
public class StringToMoodConverter implements Converter<String, Mood> {
    @Override
    public Mood convert(String source) {
        return Mood.from(source);
    }
}
