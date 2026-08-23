package com.example.musing_BE.auth.domain;

public record SocialUserInfo(
        SocialProvider provider,
        String providerId,
        String email,
        String nickname
) {}
