# oneword_BE

Spring Boot 기반 뮤징 백엔드입니다.

## 빠른 시작
```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 프로파일
- 기본값(프로파일 미지정): 운영 안전 설정(`ddl-auto=validate`, `sql.init.mode=never`)
- 로컬 개발: `SPRING_PROFILES_ACTIVE=local`
- 운영 실행 예시:
```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:mysql://localhost:3306/musing?serverTimezone=Asia/Seoul&characterEncoding=UTF-8 \
DB_USERNAME=... \
DB_PASSWORD=... \
CORS_ALLOWED_ORIGIN_PATTERNS=https://your-app.com \
APP_JWT_SECRET=... \
APP_ACCESS_TOKEN_SECONDS=3600 \
APP_REFRESH_TOKEN_SECONDS=1209600 \
GOOGLE_CLIENT_ID=... \
APPLE_CLIENT_ID=... \
KAKAO_APP_ID=... \
NAVER_CLIENT_ID=... \
NAVER_CLIENT_SECRET=... \
./gradlew bootRun
```

운영 환경에서는 Flyway 마이그레이션 적용 후 `ddl-auto=validate`로 동작합니다.

### 인증 환경변수
| 변수 | 설명 |
|---|---|
| `APP_JWT_SECRET` | 자체 JWT 서명 키. **32바이트 이상**, 절대 커밋 금지 |
| `APP_ACCESS_TOKEN_SECONDS` | 액세스 토큰 수명(초) |
| `APP_REFRESH_TOKEN_SECONDS` | 리프레시 토큰 수명(초) |
| `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` | id_token의 `aud` 검증값 |
| `KAKAO_APP_ID` | 카카오 access_token_info의 `app_id` 검증값 |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 네이버 인가 코드 교환용 |

> 뒤 네 줄은 **"우리 앱에 발급된 소셜 자격증명인가"를 확인하는 값**입니다. 비워 두면 해당
> 제공자 로그인이 500으로 거부됩니다 — 검증 없이 통과시키면 제3자가 자기 앱에서 모은 토큰으로
> 남의 계정에 로그인할 수 있기 때문에, 설정 누락은 조용히 넘기지 않고 실패시킵니다.

## DB 변경 규칙 (Flyway)
- 신규 스키마 변경은 **반드시** `src/main/resources/db/migration`에 버전 파일로 추가합니다.
- 파일명 규칙: `V{version}__{description}.sql` (예: `V4__add_user_status.sql`)
- 엔티티 변경을 먼저 배포하지 말고, 마이그레이션을 포함한 상태로 배포합니다.
- 애플리케이션은 모든 프로파일(local/test/prod)에서 `ddl-auto=validate`로 동작하며, 스키마 생성/변경은 Flyway만 담당합니다.