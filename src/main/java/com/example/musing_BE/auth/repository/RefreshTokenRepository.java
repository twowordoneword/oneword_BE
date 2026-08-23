package com.example.musing_BE.auth.repository;

import com.example.musing_BE.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    long countByUserId(Long userId);
    void deleteByUserId(Long userId);
    void deleteByTokenHash(String tokenHash);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
