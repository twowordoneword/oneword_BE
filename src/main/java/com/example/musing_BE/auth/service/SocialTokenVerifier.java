package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.domain.SocialLoginCommand;
import com.example.musing_BE.auth.domain.SocialUserInfo;

public interface SocialTokenVerifier {
    SocialUserInfo verify(SocialLoginCommand command);
}
