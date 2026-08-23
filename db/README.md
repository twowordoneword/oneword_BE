# DB 운영 방식

## 기준
- 스키마 정본은 `src/main/resources/db/migration/*.sql` (Flyway)
- 앱 시작 시 Flyway가 마이그레이션을 적용
- 운영 프로파일은 `ddl-auto=validate`만 사용

## 로컬 개발
1. MySQL/MariaDB 준비
```bash
docker compose up -d
```
2. 앱 실행 (`local` 프로파일 기본)
```bash
./gradlew bootRun
```

> `application-local.yaml`에서 로컬 기본값(`DB_*`, CORS `*`)을 제공한다.

## 프로파일 요약
- `local`: 개발 편의 설정 + `data.sql` 시드 실행
- `prod`: 시드 비활성화, SQL 로그 비활성화, 스키마 자동 변경 금지

## 참고
- `db/schema.sql`은 초기 구조 참고용 문서다.
- 신규 스키마 변경은 반드시 Flyway 마이그레이션 파일로 추가한다.
