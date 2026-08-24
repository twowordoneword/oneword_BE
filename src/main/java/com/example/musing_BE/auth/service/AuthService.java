package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.domain.SocialLoginCommand;
import com.example.musing_BE.auth.domain.SocialProvider;
import com.example.musing_BE.auth.domain.SocialUserInfo;
import com.example.musing_BE.auth.dto.*;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.diary.repository.DiaryRepository;
import com.example.musing_BE.diary.repository.DiaryTrackRepository;
import com.example.musing_BE.security.CurrentUserProvider;
import com.example.musing_BE.user.entity.User;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SocialTokenVerifier socialTokenVerifier;
    private final SocialUserRegistrar socialUserRegistrar;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final AppJwtService appJwtService;
    private final CurrentUserProvider currentUserProvider;
    private final DiaryRepository diaryRepository;
    private final DiaryTrackRepository diaryTrackRepository;

    /**
     * 메서드 전체를 트랜잭션으로 묶지 않는다. 소셜 검증은 외부 네트워크 호출이라 응답이 늦어질 수
     * 있는데, 그동안 DB 커넥션을 붙잡고 있으면 커넥션 풀이 먼저 마른다.
     * 사용자 등록과 토큰 발급은 각자 짧은 트랜잭션으로 처리한다.
     */
    public AuthResponse login(LoginRequest request) {
        SocialProvider provider = SocialProvider.from(request.provider());
        SocialUserInfo socialUser = socialTokenVerifier.verify(
                new SocialLoginCommand(provider, request.idToken(), request.state()));

        SocialUserRegistrar.Result result = findOrCreateUser(provider, socialUser, request.nickname());
        User user = result.user();

        AppJwtService.TokenPair tokenPair = appJwtService.issueTokenPair(user.getId());
        refreshTokenStore.issue(user.getId(), tokenPair.refreshToken(), tokenPair.refreshExpiresAt());

        return new AuthResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessExpiresIn(),
                UserProfileResponse.from(user),
                result.isNewUser()
        );
    }

    @Transactional
    public TokenRefreshResponse refresh(RefreshRequest request) {
        Long userId = appJwtService.parseRefreshToken(request.refreshToken());
        refreshTokenStore.consume(request.refreshToken(), userId);

        AppJwtService.TokenPair tokenPair = appJwtService.issueTokenPair(userId);
        refreshTokenStore.issue(userId, tokenPair.refreshToken(), tokenPair.refreshExpiresAt());
        return new TokenRefreshResponse(tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.accessExpiresIn());
    }

    public UserProfileResponse me() {
        Long userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }

    public void logout(LogoutRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        String refreshToken = request == null ? null : request.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshTokenStore.revokeAll(userId);
            return;
        }
        refreshTokenStore.revoke(userId, refreshToken);
    }

    @Transactional
    public void withdraw() {
        Long userId = currentUserProvider.getCurrentUserId();
        refreshTokenStore.revokeAll(userId);
        diaryTrackRepository.deleteByDiaryUserId(userId);
        diaryRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    /**
     * 같은 사용자의 최초 로그인이 동시에 들어오면 늦은 쪽이 유니크 제약에 걸린다.
     * 그때는 이미 앞선 트랜잭션이 행을 만들어 뒀으므로, 새 트랜잭션에서 한 번 더 조회하면 성공한다.
     * 두 번째도 실패하면 진짜 충돌이므로 그대로 올려보낸다.
     */
    private SocialUserRegistrar.Result findOrCreateUser(
            SocialProvider provider, SocialUserInfo socialUser, String nickname) {
        try {
            return socialUserRegistrar.findOrCreate(provider, socialUser, nickname);
        } catch (DataIntegrityViolationException e) {
            return socialUserRegistrar.findOrCreate(provider, socialUser, nickname);
        }
    }
}
