package com.example.musing_BE.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param idToken  제공자별 자격증명 — 구글·애플은 id_token, 카카오는 access_token,
 *                 <b>네이버는 인가 코드(authorization code)</b>
 * @param state    네이버 로그인에만 필요한 state 값. 그 외 제공자는 비워 둔다.
 */
public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String idToken,
        String state,
        String nickname
) {}
