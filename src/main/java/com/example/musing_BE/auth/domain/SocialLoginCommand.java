package com.example.musing_BE.auth.domain;

/**
 * 소셜 로그인 검증 입력.
 *
 * @param provider   소셜 제공자
 * @param credential 제공자별 자격증명 — 구글·애플은 id_token, 카카오는 access_token,
 *                   네이버는 <b>인가 코드(authorization code)</b>
 * @param state      네이버 인가 코드 교환에 필요한 state. 그 외 제공자는 사용하지 않는다.
 */
public record SocialLoginCommand(SocialProvider provider, String credential, String state) {}
