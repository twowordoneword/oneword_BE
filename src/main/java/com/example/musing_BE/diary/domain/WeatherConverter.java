package com.example.musing_BE.diary.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Weather enum <-> DB 한글 문자열 변환. */
@Converter(autoApply = true)
public class WeatherConverter implements AttributeConverter<Weather, String> {
    @Override
    public String convertToDatabaseColumn(Weather attribute) {
        return attribute == null ? null : attribute.getCode();
    }
    @Override
    public Weather convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Weather.from(dbData);
    }
}
