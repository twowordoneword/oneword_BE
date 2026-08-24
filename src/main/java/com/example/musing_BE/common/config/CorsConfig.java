package com.example.musing_BE.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 설정. 허용 origin은 프로파일/환경변수로 관리한다.
 *
 * <p><b>{@code WebMvcConfigurer#addCorsMappings}가 아니라 {@link UrlBasedCorsConfigurationSource}
 * 빈으로 노출하는 이유:</b> Spring Security는 컨텍스트에 이 타입의 빈이 있을 때만
 * 필터체인에 CORS 처리를 붙인다(HttpSecurityConfiguration#applyCorsIfAvailable).
 * MVC 레벨 설정만 두면 인증 헤더가 없는 프리플라이트(OPTIONS)가 시큐리티 단계에서
 * 401로 잘려 브라우저가 본 요청을 아예 보내지 않는다.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        // 인증은 Authorization 헤더(Bearer)로만 한다. 쿠키를 쓰지 않으므로 credentials는 열지 않는다.
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
