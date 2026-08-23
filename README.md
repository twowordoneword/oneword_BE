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
./gradlew bootRun
```

운영 환경에서는 Flyway 마이그레이션 적용 후 `ddl-auto=validate`로 동작합니다.

## DB 변경 규칙 (Flyway)
- 신규 스키마 변경은 **반드시** `src/main/resources/db/migration`에 버전 파일로 추가합니다.
- 파일명 규칙: `V{version}__{description}.sql` (예: `V3__add_user_status.sql`)
- 엔티티 변경을 먼저 배포하지 말고, 마이그레이션을 포함한 상태로 배포합니다.
- 애플리케이션은 모든 프로파일(local/test/prod)에서 `ddl-auto=validate`로 동작하며, 스키마 생성/변경은 Flyway만 담당합니다.