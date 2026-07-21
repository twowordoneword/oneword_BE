package com.example.musing_BE.diary.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mood enum <-> DB 한글 문자열 변환. */
@Converter(autoApply = true)
public class MoodConverter implements AttributeConverter<Mood, String> {
    @Override
    public String convertToDatabaseColumn(Mood attribute) {
        return attribute == null ? null : attribute.getCode();
    }
    @Override
    public Mood convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Mood.from(dbData);
    }
}
