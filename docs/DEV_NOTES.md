# 개발 노트 (트러블슈팅 로그)

개발하며 마주친 문제와 해결을 기록. 같은 삽질 반복 방지 + 회고용.

---

## 2026-07-21 · iTunes 검색 API 연동 시 500 에러

### 증상
`GET /api/v1/tracks/search?q=iu` 호출 시 `500 Internal Server Error`.
우리 공통 응답 래퍼(`{success, error}`)가 아니라 Spring 기본 에러가 떠서, **우리 예외 핸들러 밖에서 터진 예외**임을 알 수 있었다.

### 원인
**iTunes Search API는 응답 Content-Type을 `text/javascript`로 준다** (`application/json`이 아님).
Spring은 응답 본문을 객체로 변환할 때 **Content-Type을 보고 적절한 메시지 컨버터(HttpMessageConverter)를 고른다.**
JSON 컨버터는 `application/json`에만 붙으므로, `text/javascript` 응답은 매핑할 컨버터가 없어 `.body(ItunesSearchResult.class)`에서 예외 발생 → 500.

### 해결
응답을 **문자열(`String`)로 먼저 받은 뒤, ObjectMapper로 직접 파싱**하도록 변경.
문자열 변환기는 Content-Type과 무관하게 `text/*`를 처리하므로 우회된다.

```java
String raw = restClient.get().uri(...).retrieve().body(String.class);
ItunesSearchResult result = objectMapper.readValue(raw, ItunesSearchResult.class);
```

- Spring Boot 4는 **Jackson 3**를 쓰므로 ObjectMapper 패키지가 `tools.jackson.databind.ObjectMapper` (Jackson 2의 `com.fasterxml.jackson.databind`가 아님).
- 응답 record에는 `@JsonIgnoreProperties(ignoreUnknown = true)`를 붙여, iTunes가 주는 수많은 필드 중 필요한 것만 매핑.

### 관련 이슈 하나 더 — 한글 쿼리 400
curl로 `?q=아이유`처럼 **URL 인코딩 안 된 한글**을 보내면 Tomcat이 `400 Bad Request`(HTML)로 거부.
- curl 테스트: `curl -G ".../search" --data-urlencode "q=아이유"` 로 인코딩해서 전송.
- 실제 앱은 Flutter `http` 패키지가 쿼리 파라미터를 자동 인코딩하므로 문제없음.

### 교훈
- 500인데 응답이 우리 래퍼 형식이 아니면 → 우리 핸들러가 못 잡은 예외(외부 연동/직렬화 쪽 의심).
- 외부 API 붙일 땐 **Content-Type을 먼저 확인**. 표준 JSON이 아니면 문자열로 받아 직접 파싱.
