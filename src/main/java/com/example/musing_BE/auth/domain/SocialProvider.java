package com.example.musing_BE.auth.domain;

import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;

public enum SocialProvider {
    KAKAO("kakao"),
    NAVER("naver"),
    GOOGLE("google"),
    APPLE("apple");

    private final String code;

    SocialProvider(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SocialProvider from(String value) {
        for (SocialProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(value)) {
                return provider;
            }
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
    }
}
