# Review instructions

한마디(뮤징) 백엔드 — Spring Boot 4 / Java 21 / JPA / MySQL.
개인 프로젝트이므로 실용성을 우선한다. 완벽한 프로덕션 기준이 아니라
"이대로 머지하면 터지거나 새는가"를 기준으로 본다.

## 🔴 Important의 기준

다음만 Important로 올린다.

- 시크릿 노출: API 키·토큰·DB 비밀번호가 코드나 설정에 하드코딩됨
- 잘못된 로직: 조건·경계값·널 처리가 틀려서 실제로 다른 결과가 나옴
- 예외가 500으로 새는 경로: `GlobalExceptionHandler`가 잡지 못하는 예외 타입
- 트랜잭션 경계 오류: 쓰기 작업에 `@Transactional` 누락, readOnly인데 쓰기 시도
- 유니크 제약·orphanRemoval 충돌로 런타임에 실패하는 JPA 코드
- 외부 API 호출에 타임아웃이 없어 스레드가 무한 대기할 수 있는 경우

네이밍, 포맷, 리팩터링 제안, 주석 스타일은 전부 Nit이다.

## Nit 상한

한 리뷰에 Nit은 최대 5개까지만 인라인으로 단다.
더 있으면 요약에 "이 외 N건 유사" 한 줄로 적는다.
Important가 하나도 없으면 요약 첫 줄을 "머지를 막을 이슈 없음"으로 시작한다.

## 이 레포에서 항상 확인할 것

- **예외 → HTTP 상태 매핑**: 새 서비스 메서드가 던지는 예외가 `ErrorCode`에
  정의돼 있고 `GlobalExceptionHandler`에 핸들러가 있는지. 특히
  `DateTimeParseException`처럼 `IllegalArgumentException`을 상속하지 **않는**
  예외는 별도 핸들러가 없으면 500으로 샌다.
- **응답 포맷 일관성**: 모든 정상·에러 응답이 `ApiResponse` 래퍼를 타는지.
  Spring이 자체 생성하는 에러 바디(`HttpMessageNotReadableException` 등)로
  빠지는 경로가 새로 생기지 않았는지.
- **N+1 쿼리**: 컬렉션을 순회하며 lazy 연관을 건드리는 조회. 특히
  `Diary` → `diaryTracks` → `track` 경로. fetch join이나 `@EntityGraph`로
  해결 가능한지 함께 제시한다.
- **외부 API 클라이언트**: `RestClient`에 connect/read 타임아웃이 설정돼 있고,
  상대 서버 장애가 500으로 그대로 전파되지 않는지.
- **DEV_USER_ID 확산**: 인증 미구현 상태의 `DEV_USER_ID = 1L` 하드코딩이
  새 클래스로 복사·확산되고 있지 않은지. 늘어나면 지적한다.
- **날짜·타임존**: 일기 날짜는 KST 기준이다. `LocalDate.now()`를 타임존 인자
  없이 호출하는 코드는 지적한다.
- **쿼리 스코프**: 유저 스코프(`user.id`) 없이 전체 테이블을 훑는 조회.

## 리포트하지 말 것

- Gradle 래퍼(`gradlew`, `gradle/wrapper/`), `build/` 디렉터리
- 테스트 커버리지가 없다는 지적 (알고 있고 의도적이다)
- Lombok 사용 여부에 대한 취향 지적
- `application.yaml`의 `ddl-auto: update`, 로컬 DB 계정, CORS 전체 허용 —
  개발 단계임을 인지하고 있다. 단, 이 값들이 **운영 프로파일**로 넘어가거나
  실제 크리덴셜이 커밋되면 그건 Important다.

## 근거 요구

동작에 대한 주장은 추측이 아니라 `파일:줄` 인용으로 뒷받침한다.
이름만 보고 "아마 이럴 것"이라고 추론한 지적은 달지 않는다.

## 재리뷰

이미 한 번 리뷰한 PR에 다시 돌 때는 새 Nit을 만들지 않는다.
새로 생긴 Important만 보고한다.
