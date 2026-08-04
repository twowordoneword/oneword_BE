# 코드 개념 노트

백엔드 코드에서 처음 보는 구조·개념 정리. (학습·회고용)

---

## 1. 계층 구조 — Controller → Service → Repository / Client

요청이 이 순서로 흐른다. 각 층은 역할이 하나씩이라 코드가 안 엉킨다.

```
[프론트] → Controller → Service → Repository (DB)
                              └→ Client (외부 API)
```

- **Controller** — HTTP 요청/응답 담당. URL·파라미터 받고, 결과를 JSON으로 돌려줌. "접수 창구".
- **Service** — 비즈니스 로직. "무엇을 할지" 결정. 여러 Repository/Client를 조합.
- **Repository** — 우리 DB와 대화 (JpaRepository). 저장·조회.
- **Client** — **외부 서버(남의 API)와 대화**. ← 이번에 처음 나온 것.

## 2. "Client"란?

내 서버가 **다른 서버의 API를 호출하는 쪽**을 클라이언트라고 한다.
- 프론트가 우리 백엔드를 부를 때 → 프론트가 "클라이언트", 우리가 "서버".
- **우리 백엔드가 iTunes를 부를 때 → 우리가 "클라이언트", iTunes가 "서버".**

즉 우리 서버는 상황에 따라 서버도 되고 클라이언트도 된다.

| | Repository | Client |
|---|---|---|
| 대화 상대 | 우리 DB (MariaDB) | 외부 API (iTunes 등) |
| 방식 | SQL (JPA가 대신) | HTTP 요청 |
| 예시 | `DiaryRepository` | `ItunesClient` |

`ItunesClient`를 따로 클래스로 뺀 이유: "iTunes와 통신하는 코드"를 한 곳에 모아두면, Service는 iTunes의 세부사항(URL·파싱)을 몰라도 되고, 나중에 소스를 바꿔도 이 파일만 고치면 된다.

## 3. RestClient — Spring의 HTTP 클라이언트

외부에 HTTP 요청을 보내는 Spring 도구. 브라우저·curl이 하는 일을 코드로 하는 것.

```java
RestClient restClient = RestClient.builder()
        .baseUrl("https://itunes.apple.com")   // 기본 주소
        .build();
```

## 4. ItunesClient 한 줄씩 뜯어보기

```java
String raw = restClient.get()                    // ① GET 요청
        .uri(uriBuilder -> uriBuilder.path("/search")   // ② 주소: baseUrl + /search
                .queryParam("term", term)               //    ?term=검색어
                .queryParam("media", "music")           //    &media=music
                .queryParam("entity", "song")
                .queryParam("limit", limit)
                .queryParam("country", country)
                .build())
        .retrieve()                              // ③ 응답 받기
        .body(String.class);                     // ④ 본문을 문자열로
```

- **① `.get()`** — HTTP GET (조회). 저장이면 `.post()`.
- **② `.uri(...)`** — 최종 주소 조립. `queryParam`이 `?key=value&...`를 만들어줌. (한글·특수문자 인코딩도 자동)
  → 실제 호출되는 주소: `https://itunes.apple.com/search?term=iu&media=music&entity=song&limit=5&country=KR`
- **③ `.retrieve()`** — 요청 보내고 응답 가져오기.
- **④ `.body(String.class)`** — 응답 본문을 문자열로. (원래는 `.body(내클래스.class)`로 바로 객체 변환하지만, iTunes는 Content-Type이 이상해서 문자열로 받아 직접 파싱 — 상세는 `DEV_NOTES.md` 참고)

그다음 문자열을 객체로 파싱:

```java
ItunesSearchResult result = objectMapper.readValue(raw, ItunesSearchResult.class);
```

## 5. 내부 record로 응답 매핑

iTunes 응답 JSON은 필드가 수십 개다. 그중 **필요한 것만** 담을 그릇(record)을 만든다.

```java
@JsonIgnoreProperties(ignoreUnknown = true)   // 안 쓰는 필드는 무시
record ItunesSong(String trackName, String artistName,
                  String collectionName, String artworkUrl100, String previewUrl) { ... }
```

- `@JsonIgnoreProperties(ignoreUnknown = true)` — iTunes가 주는 나머지 필드는 버리고, 여기 적은 것만 매핑. (안 붙이면 "모르는 필드" 에러 가능)
- iTunes 필드명(`trackName`)을 우리 필드명(`name`)으로 바꾸는 변환은 `toTrackInfo()`에서.

## 6. 왜 이렇게 나누나 (요약)

Controller는 "요청 받고 응답", Service는 "판단", Client는 "외부와 통신", Repository는 "DB와 통신".
각자 한 가지만 하니까 — 버그 찾기 쉽고, 한 부분 바꿔도 다른 데 안 깨지고, 테스트하기 쉽다.
