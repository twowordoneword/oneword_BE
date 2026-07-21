# DB 설정 (MySQL / MariaDB)

## 결론: 로컬 MariaDB 그대로 써도 됩니다
MariaDB는 MySQL 드롭인 호환이라 Docker로 MySQL을 따로 안 띄워도 됩니다.
Spring은 **MySQL 다이얼렉트**로 동일하게 동작합니다. 둘 중 편한 쪽을 고르세요.

### A안) 이미 깔린 로컬 MariaDB 사용
```bash
mysql -u root -p -e "CREATE DATABASE musing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p musing < db/schema.sql
```

### B안) Docker로 MySQL (팀 환경 통일용)
```bash
docker compose up -d          # db/schema.sql이 최초 1회 자동 적용됨
docker compose logs -f mysql  # 준비 상태 확인
```

## Spring 설정

`build.gradle` 의존성 추가:
```gradle
runtimeOnly 'com.mysql:mysql-connector-j'   // MariaDB에도 정상 동작
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
```

`src/main/resources/application.yaml`:
```yaml
spring:
  application:
    name: musing_BE
  datasource:
    url: jdbc:mysql://localhost:3306/musing?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: musing        # Docker면 musing / 로컬 MariaDB면 root 등
    password: musing
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate    # schema.sql로 스키마 관리 → validate 권장 (개발 초기엔 update도 가능)
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
    show-sql: true
```

> `ddl-auto`: schema.sql을 정본으로 쓰면 `validate`, JPA 엔티티로 스키마를 생성하게 하려면 `update`.
> mood/weather는 한글 문자열을 그대로 저장하므로 URL/DB 모두 `utf8mb4` 필수.
