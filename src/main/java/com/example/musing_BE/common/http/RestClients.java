package com.example.musing_BE.common.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 API용 {@link RestClient} 생성기.
 *
 * <p>클라이언트마다 같은 설정을 복사하고 있었다(iTunes·Apple 차트·FreqBlog 셋).
 * 타임아웃 정책을 바꿀 때 세 곳을 고쳐야 했고, 한 곳만 고치면 조용히 어긋난다.
 *
 * <p><b>타임아웃은 선택이 아니다.</b> 없으면 상대 서버가 응답을 안 줄 때 우리 스레드가
 * 영원히 기다리고, 그런 요청이 쌓이면 서버 전체가 멈춘다.
 */
public final class RestClients {

    private RestClients() {}

    /** 외부 API 기본값 — 연결 3초, 응답 5초. */
    public static RestClient create(String baseUrl) {
        return create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    /**
     * baseUrl 없이 매 호출마다 절대 URL을 주는 경우(호스트가 여러 개인 소셜 인증 등)용.
     * 타임아웃 기본값은 {@link #create(String)}과 같다.
     */
    public static RestClient createDefault() {
        return build(null, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    /**
     * @param connectTimeout 연결 자체가 안 될 때까지 기다릴 시간
     * @param readTimeout    연결은 됐는데 데이터가 안 올 때까지 기다릴 시간
     */
    public static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return build(baseUrl, connectTimeout, readTimeout);
    }

    private static RestClient build(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
