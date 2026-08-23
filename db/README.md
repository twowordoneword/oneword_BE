# DB 운영 방식

## 기준
- 스키마 정본은 `src/main/resources/db/migration/*.sql` (Flyway)
- 앱 시작 시 Flyway가 마이그레이션을 적용
- 운영 프로파일은 `ddl-auto=validate`만 사용
- local/test/prod 모두 `ddl-auto=validate`를 사용하며, 스키마 생성/변경은 Flyway만 수행

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
- `test`: CI/테스트 환경, Flyway migration + Hibernate validate
- `prod`: 시드 비활성화, SQL 로그 비활성화, 스키마 자동 변경 금지

## 기존 스키마 환경 이행 (baseline-on-migrate)
1. 기존 DB에 Flyway 이력 테이블(`flyway_schema_history`)이 없다면, 앱 시작 시 `baseline-on-migrate=true`로 baseline 처리된다.
2. baseline 이후 신규 버전 마이그레이션만 적용된다.
3. 기존 환경에서 수동 반영된 컬럼/인덱스가 있을 수 있으므로, 후속 마이그레이션은 가능한 한 안전한(재적용 가능한) 방식으로 작성한다.

## 마이그레이션 작성 규칙
- 위치: `src/main/resources/db/migration`
- 파일명: `V{version}__{description}.sql` (`description`은 소문자 스네이크/단어 구분 권장)
- 버전은 단조 증가해야 하며, 이미 배포된 버전 파일은 수정하지 않는다.
- 스키마 변경은 migration 파일로만 반영한다(엔티티 변경 + `ddl-auto=update` 금지).
- `db/schema.sql`은 참고 문서이며 배포 기준 원본이 아니다.
