package com.example.musing_BE.auth.service;

import com.example.musing_BE.auth.entity.RefreshToken;
import com.example.musing_BE.auth.repository.RefreshTokenRepository;
import com.example.musing_BE.common.exception.BusinessException;
import com.example.musing_BE.common.exception.ErrorCode;
import com.example.musing_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리프레시 토큰 저장소.
 *
 * <p>토큰 원문은 어디에도 남기지 않는다. 저장·조회·삭제 모두 해시로만 하므로
 * DB가 통째로 유출돼도 그 값으로 재발급을 받을 수는 없다.
 *
 * <p>시각 비교는 전부 주입받은 시계 하나로 통일한다. 발급은 UTC, 만료 검사는 서버 기본 존처럼
 * 서로 다른 기준을 쓰면 타임존 차이만큼 수명이 조용히 어긋난다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    /** 한 사용자가 동시에 유지할 수 있는 세션 수. 넘으면 가장 오래된 것부터 밀어낸다. */
    public static final int MAX_ACTIVE_PER_USER = 5;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock kstClock;

    @Transactional
    public void issue(Long userId, String rawToken, LocalDateTime expiresAt) {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(kstClock));

        // 로그인만 반복해도 행이 무한정 쌓이지 않도록 사용자별 상한을 둔다.
        List<RefreshToken> active = refreshTokenRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        int overflow = active.size() + 1 - MAX_ACTIVE_PER_USER;
        if (overflow > 0) {
            refreshTokenRepository.deleteAll(active.subList(0, overflow));
        }

        refreshTokenRepository.save(RefreshToken.issue(
                userRepository.getReferenceById(userId),
                refreshTokenHasher.hash(rawToken),
                expiresAt
        ));
    }

    /** 회전: 제시된 토큰을 검증하고 즉시 폐기한다. 같은 토큰을 두 번 쓰면 두 번째는 실패한다. */
    @Transactional
    public void consume(String rawToken, Long userId) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!saved.getUser().getId().equals(userId)
                || saved.getExpiresAt().isBefore(LocalDateTime.now(kstClock))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        refreshTokenRepository.delete(saved);
    }

    /** 전체 기기 로그아웃. */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /** 이 기기만 로그아웃. 남의 토큰을 넘겨도 소유자가 다르면 아무 일도 일어나지 않는다. */
    @Transactional
    public void revoke(Long userId, String rawToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(rawToken))
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(refreshTokenRepository::delete);
    }
}
