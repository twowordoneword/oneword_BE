package com.example.musing_BE.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 길이 상한은 DB 컬럼(users.nickname VARCHAR(50))과 외부 호출 크기를 기준으로 잡는다.
 * 상한이 없으면 긴 값이 DB 단계까지 내려가 400 대신 제약 위반 오류로 응답된다.
 *
 * @param idToken  제공자별 자격증명 — 구글·애플·카카오는 id_token,
 *                 <b>네이버는 인가 코드(authorization code)</b>
 * @param state    네이버 로그인에만 필요한 state 값. 그 외 제공자는 비워 둔다.
 */
public record LoginRequest(
        @NotBlank @Size(max = 20) String provider,
        @NotBlank @Size(max = 8192) String idToken,
        @Size(max = 255) String state,
        @Size(max = 50) String nickname
) {}
