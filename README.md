# oneword_BE

Spring Boot 기반 뮤징 백엔드입니다.

## 빠른 시작
```bash
docker compose up -d
./gradlew bootRun
```

## 프로파일
- 기본 프로파일: `local`
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