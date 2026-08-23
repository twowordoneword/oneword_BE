package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.auth.dto.*;
import com.example.musing_BE.auth.entity.RefreshToken;
import com.example.musing_BE.auth.repository.RefreshTokenRepository;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.diary.repository.DiaryTrackRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final SocialTokenVerifier socialTokenVerifier;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppJwtService appJwtService;
    private final RefreshTokenHasher refreshTokenHasher;
    private final CurrentUserProvider currentUserProvider;
    private final DiaryRepository diaryRepository;
    private final DiaryTrackRepository diaryTrackRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        SocialProvider provider = SocialProvider.from(request.provider());
        SocialUserInfo socialUser = socialTokenVerifier.verify(provider, request.idToken());

        final boolean[] isNewUser = {false};
        User user = userRepository.findByProviderAndProviderId(provider.code(), socialUser.providerId())
                .orElseGet(() -> {
                    isNewUser[0] = true;
                    String email = socialUser.email();
                    if (email == null || email.isBlank()) {
                        email = provider.code() + "_" + socialUser.providerId() + "@social.local";
                    }
                    String nickname = request.nickname();
                    if (nickname == null || nickname.isBlank()) {
                        nickname = socialUser.nickname() != null && !socialUser.nickname().isBlank()
                                ? socialUser.nickname()
                                : provider.code() + "_" + socialUser.providerId();
                    }
                    return userRepository.save(User.create(email, nickname, provider.code(), socialUser.providerId()));
                });

        if (!user.getProvider().equals(provider.code())) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.updateNickname(request.nickname());
        }

        AppJwtService.TokenPair tokenPair = appJwtService.issueTokenPair(user.getId());
        saveRefreshToken(user, tokenPair.refreshToken(), tokenPair.refreshExpiresAt());

        return new AuthResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessExpiresIn(),
                UserProfileResponse.from(user),
                isNewUser[0]
        );
    }

    @Transactional
    public TokenRefreshResponse refresh(RefreshRequest request) {
        Long userId = appJwtService.parseRefreshToken(request.refreshToken());
        String tokenHash = refreshTokenHasher.hash(request.refreshToken());

        RefreshToken saved = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!saved.getUser().getId().equals(userId) || saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = saved.getUser();
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        AppJwtService.TokenPair tokenPair = appJwtService.issueTokenPair(user.getId());
        saveRefreshToken(user, tokenPair.refreshToken(), tokenPair.refreshExpiresAt());
        return new TokenRefreshResponse(tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.accessExpiresIn());
    }

    public UserProfileResponse me() {
        Long userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void logout() {
        Long userId = currentUserProvider.getCurrentUserId();
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void withdraw() {
        Long userId = currentUserProvider.getCurrentUserId();
        refreshTokenRepository.deleteByUserId(userId);
        diaryTrackRepository.deleteByDiaryUserId(userId);
        diaryRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    private void saveRefreshToken(User user, String refreshToken, LocalDateTime expiresAt) {
        refreshTokenRepository.save(
                RefreshToken.issue(user, refreshTokenHasher.hash(refreshToken), expiresAt)
        );
    }
}
