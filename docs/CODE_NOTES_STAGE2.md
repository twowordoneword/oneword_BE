# 코드 개념 노트 ② — 차트 수집 배치

`CODE_NOTES.md`에 이어, 추천 2단계에서 새로 나온 코드·개념 정리. (학습·회고용)

이번에 만든 건 **배치(batch)** 다. 사용자 요청과 무관하게 혼자 도는 코드.

```
[지금까지] 사용자 요청 → Controller → Service → 응답      (요청이 있어야 돎)
[이번]     시계 또는 관리자 → Scheduler → Service → DB    (요청 없이 혼자 돎)
```

---

## 1. enum — 정해진 값만 담는 타입

```java
public enum TrackOrigin {
    USER,    // 사용자가 일기에 붙임
    CHART,   // 차트 배치가 넣음
    SURVEY   // 설문으로 모은 가수의 곡
}
```

`String origin = "chart"` 로 해도 되는데 enum을 쓰는 이유:

- **오타가 컴파일 에러**가 된다. `"chrat"`는 실행해봐야 알지만 `TrackOrigin.CHRAT`은 IDE가 바로 잡아준다.
- 가능한 값이 코드에 문서화된다. 나중에 봐도 "origin에 뭐가 들어가지?" 를 안 찾아도 된다.
- `switch`에서 빠뜨린 케이스를 컴파일러가 경고해준다.

### DB에 어떻게 저장되나

```java
@Enumerated(EnumType.STRING)   // ← 이게 핵심
@Column(length = 10)
private TrackOrigin origin;
```

`@Enumerated`가 없으면 JPA는 enum을 **순서(0, 1, 2)** 로 저장한다. 그러면 나중에 enum 중간에 값을 하나 끼워 넣는 순간 **기존 데이터의 의미가 통째로 바뀐다**(USER였던 0이 다른 게 됨). 그래서 실무에서는 거의 항상 `EnumType.STRING`을 쓴다. DB에는 `'CHART'` 문자열로 들어간다.

> 참고: 기존 `Mood`·`Weather`는 한글 문자열로 저장하려고 다른 방식을 썼다(ADR-004). 여기선 내부용이라 enum 이름 그대로 저장한다.

---

## 2. 유틸 클래스 — 인스턴스를 만들 수 없는 클래스

```java
public final class KoreanText {

    private KoreanText() {}   // ← 생성자를 private으로 막음

    public static boolean containsHangul(String s) {
        if (s == null || s.isBlank()) return false;
        return s.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
    }
}
```

- `final` — 상속 금지. 이 클래스를 물려받아 변형할 이유가 없다.
- `private` 생성자 — `new KoreanText()` 를 못 하게 막는다. 상태(필드)가 없으니 객체를 만들 이유가 없고, `KoreanText.containsHangul(...)` 처럼 **바로 호출**해서 쓴다.
- `static` 메서드 — 객체 없이 클래스 이름으로 호출.

### `0xAC00 ~ 0xD7A3` 이 뭔가

유니코드에서 **완성형 한글(가~힣)의 번호 범위**다. `가`가 0xAC00(44032), `힣`이 0xD7A3(55203).

```java
s.chars()                                    // 문자열 → 문자 코드 스트림
 .anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3)  // 하나라도 범위 안이면 true
```

`anyMatch`는 **하나라도 조건을 만족하면 즉시 true**를 내고 멈춘다(단축 평가). "아이유"는 첫 글자에서 바로 끝나고, "Post Malone"은 끝까지 훑고 false.

### 왜 따로 뺐나

원래 `MoodWeatherSeasonMapper`에 같은 코드가 있었다.

```java
// 전 (중복)
return seedArtist.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3) ? "KR" : "US";

// 후 (위임)
return KoreanText.containsHangul(seedArtist) ? "KR" : "US";
```

같은 판별을 두 곳에서 쓰게 되면서 한 곳으로 모았다. 나중에 판별 규칙을 고칠 때(예: 자음·모음만 있는 경우 처리) **한 군데만** 고치면 된다.

---

## 3. record — 데이터만 담는 짧은 클래스

```java
public record CollectedTrack(
        Long trackId, Long artistId, String name, String artist,
        String album, String artworkUrl, String previewUrl, String genre
) {}
```

이 한 줄이 옛날 방식으로는 **50줄짜리 클래스**다. 자바가 자동으로 만들어 주는 것:

- 생성자 `new CollectedTrack(1L, 2L, ...)`
- 게터 — 단 이름이 `getName()`이 아니라 **`name()`** 이다
- `equals`, `hashCode`, `toString`
- **모든 필드가 final** — 한 번 만들면 못 바꾼다(불변)

### 왜 `TrackInfoResponse`를 그대로 안 쓰고 새로 만들었나

```java
TrackInfoResponse — name, artist, album, artworkUrl, previewUrl        (응답용)
CollectedTrack    — + trackId, artistId, genre                          (수집용)
```

수집할 때는 **아티스트 ID**(카탈로그를 펼치는 씨앗)와 **장르**가 필요한데, 프론트에 내려줄 응답에는 그게 필요 없다. 한 DTO에 다 넣으면 응답에 쓸데없는 필드가 섞이고 "이 필드는 언제 채워지나?"가 애매해진다. **용도별로 DTO를 나누는 게 정석**이다.

---

## 4. 엔티티는 왜 setter를 안 두나

`Track`에 필드를 추가했지만 `setOrigin()` 같은 걸 만들지 않았다. 대신:

```java
/** 수집 배치가 기존 곡을 다시 만났을 때 비어 있는 칸만 채운다. */
public void fillCollected(String genre, Boolean isKorean, TrackOrigin origin) {
    if (this.genre == null && genre != null) this.genre = genre;
    if (this.isKorean == null && isKorean != null) this.isKorean = isKorean;
    if (this.origin == null && origin != null) this.origin = origin;
}
```

setter를 열어두면 **아무나 아무 때나** 값을 바꿀 수 있어서, 나중에 "이 값이 왜 바뀌었지?"를 추적하기 어렵다. 대신 **의미 있는 이름의 메서드**를 두면 규칙을 그 안에 가둘 수 있다.

여기 담긴 규칙: `null`일 때만 채운다. 그래서 **사용자가 먼저 붙인 곡(origin=USER)을 차트 배치가 CHART로 덮어쓰지 않는다.** 최초 유입 경로가 보존된다.

```java
/** 감정값 배치가 특성을 기록한다. 곡당 1회. */
public void updateFeatures(BigDecimal valence, BigDecimal energy,
                           BigDecimal acousticness, BigDecimal tempo, String source) {
    this.valence = valence;
    ...
    this.featuresFetchedAt = LocalDateTime.now();   // 기록 시각을 안에서 찍음
}
```

호출하는 쪽이 시각을 깜빡할 수 없게 **엔티티가 스스로 찍는다.**

### JPA 더티 체킹 — save()를 왜 안 부르나

```java
existing.updateMeta(...);      // 값만 바꾸고 끝. save() 없음
existing.fillCollected(...);
```

트랜잭션 안에서 **DB에서 꺼내온 엔티티**는 JPA가 계속 감시한다. 트랜잭션이 끝날 때 처음 상태와 비교해서 **바뀐 게 있으면 자동으로 UPDATE**를 날린다. 이걸 더티 체킹(dirty checking)이라 한다. 그래서 `save()`를 부를 필요가 없다.

(반대로 `new`로 만든 새 객체는 JPA가 모르므로 `save()`가 필요하다.)

---

## 5. AppleChartClient — 외부 API 부르기

### 왜 클라이언트를 또 만들었나

`ItunesClient`가 이미 있는데 새로 만든 이유는 **주소가 다르기 때문**이다.

```java
ItunesClient      → https://itunes.apple.com
AppleChartClient  → https://rss.marketingtools.apple.com
```

`RestClient`는 `baseUrl`을 하나만 갖는다. 서버가 다르면 클라이언트도 따로 두는 게 자연스럽다.

### 타임아웃을 꼭 걸어야 하는 이유

```java
private static SimpleClientHttpRequestFactory timeoutFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000); // 연결 3초
    factory.setReadTimeout(5000);    // 응답 5초
    return factory;
}
```

타임아웃이 없으면 상대 서버가 응답을 안 줄 때 **우리 스레드가 영원히 기다린다.** 그런 요청이 쌓이면 서버 전체가 멈춘다. 외부 API를 부를 때 타임아웃은 선택이 아니라 필수다.

- `connectTimeout` — "연결 자체가 안 됨"까지 기다릴 시간
- `readTimeout` — "연결은 됐는데 데이터가 안 옴"까지 기다릴 시간

### 경로를 문자열로 만드는 이유

```java
String path = "/api/v2/%s/music/most-played/%d/songs.json".formatted(storefront, capped);
```

이 API는 값이 **쿼리 파라미터가 아니라 경로 자체**에 들어간다(`/kr/.../100/songs.json`). 그래서 `queryParam` 대신 문자열을 조립한다. `formatted`는 `%s`(문자열)·`%d`(정수) 자리에 값을 끼워 넣는다.

### 방어적으로 값 다듬기

```java
int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
```

`Math.max(limit, 1)`로 **1 미만을 1로** 올리고, `Math.min(..., 100)`으로 **100 초과를 100으로** 내린다. 설정에 200이 적혀 있어도 안전하게 100이 된다. (실측에서 200은 빈 응답이 오는 걸 확인했다.)

### JSON 응답을 자바 객체로

응답이 이렇게 생겼다:

```json
{ "feed": { "results": [ { "id": "1887671067", "artistId": "1831651635", ... } ] } }
```

구조를 그대로 record로 옮긴다:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
record ChartResponse(Feed feed) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record Feed(String country, List<ChartEntry> results) {}
```

**`@JsonIgnoreProperties(ignoreUnknown = true)` 가 중요하다.** 애플 응답에는 `copyright`, `icon`, `availableCountries` 등 우리가 안 쓰는 필드가 잔뜩 있다. 이 설정이 없으면 **모르는 필드를 만나는 순간 예외**가 난다. 있으면 조용히 무시한다. 나중에 애플이 필드를 추가해도 우리 코드가 안 깨진다.

### 문자열로 오는 ID 다루기

차트 응답은 ID를 **문자열**로 준다(`"id": "1887671067"`). 우리는 숫자로 쓰고 싶다.

```java
public Long trackIdAsLong() { return parse(id); }

private static Long parse(String v) {
    if (v == null || v.isBlank()) return null;
    try {
        return Long.valueOf(v.trim());
    } catch (NumberFormatException e) {
        return null;    // 이상한 값이면 null → 호출부에서 걸러짐
    }
}
```

record 안에 메서드를 넣을 수 있다. 변환 실패 시 **예외를 던지지 않고 null**을 주는 건, 곡 하나 때문에 배치 전체가 죽지 않게 하려는 것이다.

---

## 6. 에러 처리 — 상황에 따라 다르게

같은 `ItunesClient` 안인데 두 방식을 섞어 썼다. 이게 이번 코드에서 제일 중요한 판단이다.

```java
// 검색 (사용자가 기다리는 중) — 실패를 알려야 한다
catch (Exception e) {
    log.warn("iTunes 검색 실패 ...");
    throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);   // → 502 응답
}

// 수집 (배치가 도는 중) — 실패해도 계속 가야 한다
catch (Exception e) {
    log.warn("iTunes {} 조회 실패 ...");
    return List.of();     // 빈 목록 주고 다음 아티스트로
}
```

**사용자 요청**은 실패를 숨기면 안 된다. 빈 결과를 주면 "곡이 없다"와 "서버가 고장났다"를 구분할 수 없다.

**배치**는 반대다. 아티스트 160명 중 3명 조회가 실패했다고 나머지 157명을 버리면 손해다. 실패한 것만 건너뛰고 계속 간다. 다음 주 배치에서 다시 시도된다.

> `List.of()`는 **비어 있는 불변 리스트**다. `new ArrayList<>()`와 달리 새 객체를 안 만들고 원소 추가도 막혀 있어, "결과 없음"을 나타내기에 적합하다.

---

## 7. ChartCollectService — 수집 본체

### LinkedHashMap으로 중복 제거 + 순서 유지

```java
Map<Long, String> artistStorefront = new LinkedHashMap<>();
...
artistStorefront.putIfAbsent(artistId, sf);
```

차트 100곡에 같은 가수가 여러 번 나온다(리센느가 2곡 등). 아티스트 ID를 **키**로 쓰면 자동으로 중복이 없어진다.

- `putIfAbsent` — **이미 있으면 덮어쓰지 않는다.** 그래서 kr에서 먼저 발견된 가수는 kr 스토어로 조회된다.
- `LinkedHashMap` — 넣은 순서를 기억한다. 일반 `HashMap`은 순서가 뒤죽박죽이라 로그를 볼 때 헷갈린다.

값에 스토어프론트를 같이 담은 이유: 그 가수를 **발견한 스토어에서** 카탈로그를 조회해야 표기(한글/영문)와 가용 곡이 맞기 때문이다.

### 흐름

```java
for (String storefront : storefronts) {          // ① kr, us 각각
    List<ChartEntry> chart = appleChartClient.topSongs(sf, chartLimit);
    for (ChartEntry e : chart) {
        trackIds.add(e.trackIdAsLong());              // 차트 곡 자체도 후보
        artistStorefront.putIfAbsent(artistId, sf);   // 가수 수집
    }
    candidates.addAll(itunesClient.lookupByIds(trackIds, sf));   // ② 미리듣기 채우기
}

for (Map.Entry<Long, String> entry : artistStorefront.entrySet()) {
    sleepBetweenCalls();                                          // ③ 속도 제한
    candidates.addAll(itunesClient.lookupArtistSongs(...));       // ④ 카탈로그 확장
}
```

②가 필요한 이유: **차트는 previewUrl을 안 준다.** 미리듣기가 없으면 감정값 분석도 앱 재생도 못 하니, ID로 다시 조회해서 채운다.

④가 이 배치의 핵심이다. 여기서 후보가 200곡 → 수천 곡으로 불어난다.

### 속도 제한 지키기

```java
private void sleepBetweenCalls() {
    if (requestDelayMs <= 0) return;
    try {
        Thread.sleep(requestDelayMs);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();   // ← 중요
        throw new IllegalStateException("수집 배치가 중단되었습니다.", e);
    }
}
```

`Thread.sleep`은 그 스레드를 지정 시간만큼 멈춘다. 3초씩 쉬면 분당 20회가 된다.

**`Thread.currentThread().interrupt()` 이 줄이 왜 있나:** `InterruptedException`이 잡히는 순간 자바는 스레드의 "중단 요청됨" 표시를 **꺼버린다.** 그대로 두면 바깥 코드는 "누가 이 작업을 멈추라고 했다"는 사실을 모르게 된다. 그래서 다시 켜주는 게 관례다. 이걸 빠뜨리는 건 자바에서 흔한 실수다.

### 필터링

```java
private boolean isUsable(CollectedTrack c) {
    if (c.name() == null || c.artist() == null) return false;
    if (c.previewUrl() == null || c.previewUrl().isBlank()) return false;
    String album = c.album() == null ? "" : c.album();
    return !album.contains("DJ Mix");
}
```

`previewUrl`이 없으면 버리는 게 이 설계의 좋은 성질이다 — **분석도 못 하고 앱에서 재생도 못 하는 곡**이라 애초에 후보가 되면 안 된다. 별도 규칙을 안 만들어도 자연히 걸러진다.

`DJ Mix` 제외는 실측에서 나온 것이다. 카탈로그에 클럽 믹스 음원이 섞여 나왔다.

---

## 8. `@Transactional` 자체 호출 함정 ★

이번에 실제로 고친 버그다. **면접에도 자주 나오는 주제.**

### 처음 짠 코드 (동작 안 함)

```java
@Service
public class ChartCollectService {

    public CollectResult collect() {
        ...
        upsert(c);          // ← 같은 클래스 안에서 호출
    }

    @Transactional          // ← 안 걸린다!
    protected boolean upsert(CollectedTrack c) { ... }
}
```

### 왜 안 걸리나

`@Transactional`은 자바 문법이 아니라 **스프링이 대신 처리해 주는 표시**다. 스프링은 진짜 객체를 감싼 **대리인(프록시) 객체**를 만들어서, 대리인을 거쳐 들어오는 호출에만 트랜잭션을 시작·커밋한다.

```
[정상] 다른 빈 → 대리인 → (트랜잭션 시작) → 진짜 객체.upsert()
[자체] 진짜 객체.collect() → 진짜 객체.upsert()      ← 대리인을 안 거침. 트랜잭션 없음
```

같은 클래스 안에서 `upsert(c)`를 부르면 **이미 진짜 객체 안**이라 대리인을 통과하지 않는다. 그래서 애노테이션이 조용히 무시된다. **에러도 안 난다.** 그래서 더 위험하다.

### 고친 방법 — 다른 빈으로 분리

```java
@Service
public class TrackUpsertService {          // 별도 클래스 = 별도 빈

    @Transactional
    public boolean upsert(CollectedTrack c, TrackOrigin origin) { ... }
}

// 호출하는 쪽
private final TrackUpsertService trackUpsertService;   // 주입받음 → 대리인이 들어옴
...
trackUpsertService.upsert(c, TrackOrigin.CHART);       // 대리인 경유 → 트랜잭션 걸림
```

덤으로 얻은 것: 트랜잭션이 **곡 하나 단위**로 끊긴다. 4,000곡을 한 트랜잭션에 묶으면 마지막 곡에서 실패했을 때 앞의 3,999곡이 전부 롤백된다. 지금은 실패한 곡만 버려진다.

> `protected`였던 것도 문제였다. 프록시는 보통 `public` 메서드만 감싼다.

### upsert 안쪽

```java
return trackRepository.findByNameAndArtist(c.name(), c.artist())
        .map(existing -> {                    // 있으면
            existing.updateMeta(...);
            existing.fillCollected(...);
            return false;                     // 신규 아님
        })
        .orElseGet(() -> {                    // 없으면
            trackRepository.save(Track.builder()...build());
            return true;                      // 신규
        });
```

`Optional`의 `map`/`orElseGet` 조합이다. `if (opt.isPresent())`로 써도 되지만 이 방식이 "있으면 A, 없으면 B"를 한 흐름으로 보여준다.

`orElseGet(() -> ...)`은 **없을 때만** 람다를 실행한다. (`orElse(...)`는 값이 있어도 인자를 먼저 평가해서 낭비가 생긴다. 저장 같은 부수효과가 있으면 특히 위험하다.)

---

## 9. `@Scheduled` — 시간이 되면 알아서 실행

```java
@Scheduled(cron = "${musing.chart.cron:0 0 3 * * MON}", zone = "Asia/Seoul")
public void run() { ... }
```

### cron 표현식 읽는 법

스프링은 **6자리**다(리눅스 cron은 5자리라 헷갈리기 쉽다).

```
0    0    3    *    *    MON
초   분   시   일   월   요일
```

→ 매주 월요일 03시 00분 00초.

`zone = "Asia/Seoul"`을 안 적으면 **서버 시간대**를 따른다. 해외 서버에 올리면 엉뚱한 시각에 돈다.

`${musing.chart.cron:...}` 은 설정 파일 값을 읽되, **없으면 `:` 뒤 기본값**을 쓴다는 뜻이다.

### 활성화 스위치

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

**이게 없으면 `@Scheduled`가 전혀 동작하지 않는다.** 애노테이션만 붙이고 스케줄러가 안 돈다며 헤매는 경우가 많다.

### 예외를 반드시 잡아야 하는 이유

```java
try {
    chartCollectService.collect();
} catch (Exception e) {
    log.error("차트 수집 배치 실패: {}", e.getMessage(), e);
}
```

스케줄러 메서드에서 예외가 밖으로 나가면 **이후 실행이 멈출 수 있다.** 잡아서 로그만 남기고 다음 주를 기다린다.

---

## 10. AdminBatchController — 수동 트리거

```java
@Value("${musing.admin.token:}")
private String adminToken;

@PostMapping("/chart")
public ApiResponse<CollectResult> collectChart(
        @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    assertAdmin(token);
    return ApiResponse.ok(chartCollectService.collect());
}

private void assertAdmin(String token) {
    if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
```

- `@Value` — 설정 파일 값을 필드에 주입. `:` 뒤는 기본값(여기선 빈 문자열).
- `@RequestHeader` — HTTP 헤더를 파라미터로 받는다. `required = false`라 헤더가 없어도 400이 아니라 우리 검사 로직으로 넘어온다.
- **`adminToken`이 비어 있으면 항상 거부.** 설정을 깜빡했을 때 아무나 배치를 돌리는 사고를 막는다. "설정 안 하면 열림"이 아니라 "설정 안 하면 잠김"이 안전한 기본값이다.

### 왜 GET이 아니라 POST인가

GET은 "조회", POST는 "무언가를 바꿈"이라는 약속이 있다. 이 요청은 DB에 수천 건을 넣으므로 POST가 맞다. GET으로 두면 브라우저 주소창이나 크롤러가 실수로 배치를 돌릴 수 있다.

### 한계 (코드에도 주석으로 남김)

인증 체계가 아직 없어서 **공유 토큰**으로 막았다. JWT를 붙이면 관리자 권한 검사로 바꿔야 한다.

---

## 11. 설정 주입 — 값을 코드 밖으로

```yaml
musing:
  chart:
    storefronts: kr,us
    songs-per-artist: 50
    request-delay-ms: 3000
```

```java
@Value("${musing.chart.storefronts:kr,us}")
private List<String> storefronts;        // 쉼표 문자열 → List로 자동 변환
```

숫자를 코드에 박지 않고 설정으로 빼는 이유: **재컴파일 없이 바꿀 수 있다.** iTunes가 생각보다 관대하면 `request-delay-ms`를 1000으로 낮춰 3배 빠르게, 차단당하면 5000으로 올리면 된다.

`storefronts`처럼 쉼표로 구분된 문자열은 스프링이 알아서 `List<String>`으로 바꿔준다.

---

## 12. 인덱스 — 조회 속도용 색인

```sql
CREATE INDEX idx_tracks_features_pending ON tracks (valence, preview_url(100));
CREATE INDEX idx_tracks_valence_energy   ON tracks (valence, energy);
```

인덱스는 책의 **찾아보기**와 같다. 없으면 DB가 4,000행을 처음부터 끝까지 훑는다(풀 스캔).

- 첫 번째 — 감정값 배치의 `WHERE valence IS NULL AND preview_url IS NOT NULL` 용
- 두 번째 — 추천의 "감정값 있는 곡" 조회용

`preview_url(100)`의 `(100)`은 **앞 100글자만 색인**한다는 뜻이다. URL 전체(500자)를 색인하면 용량이 크고, 여기선 "값이 있냐 없냐"만 보므로 앞부분이면 충분하다.

> 인덱스는 공짜가 아니다. 조회는 빨라지지만 INSERT·UPDATE는 색인도 갱신해야 해서 조금 느려진다. 그래서 자주 조회하는 조건에만 건다.

---

## 요약 — 이번에 새로 나온 개념

| 개념 | 한 줄 정리 |
|---|---|
| `enum` + `@Enumerated(STRING)` | 정해진 값만 담고, DB엔 이름 그대로 저장 |
| 유틸 클래스 | 상태 없는 함수 모음. private 생성자로 인스턴스화 차단 |
| `record` | 데이터 전용 불변 클래스. 게터가 `name()` 형태 |
| 더티 체킹 | 트랜잭션 안에서 값만 바꾸면 JPA가 알아서 UPDATE |
| `@JsonIgnoreProperties` | 모르는 JSON 필드 무시 → 외부 API 변경에 안 깨짐 |
| 타임아웃 | 외부 호출엔 필수. 없으면 스레드가 영원히 대기 |
| 에러 전략 분리 | 요청은 예외로 알리고, 배치는 건너뛰고 계속 |
| **`@Transactional` 자체 호출** | **같은 클래스 안 호출은 프록시를 안 거쳐 무시됨** |
| `orElseGet` vs `orElse` | 전자는 필요할 때만 실행 |
| `@Scheduled` + `@EnableScheduling` | 스위치를 켜야 스케줄러가 돈다. cron은 6자리 |
| 안전한 기본값 | 설정 누락 시 "열림"이 아니라 "잠김" |
| 인덱스 | 조회는 빨라지고 쓰기는 조금 느려진다 |
