package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;

public interface SocialTokenVerifier {
    SocialUserInfo verify(SocialProvider provider, String token);
}
