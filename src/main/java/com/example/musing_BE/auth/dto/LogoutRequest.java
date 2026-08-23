package com.example.musing_BE.auth.dto;

/**
 * @param refreshToken 이 기기의 리프레시 토큰. 주면 해당 세션만 끊고,
 *                     비워 두면 이 사용자의 모든 기기에서 로그아웃한다.
 */
public record LogoutRequest(String refreshToken) {}
