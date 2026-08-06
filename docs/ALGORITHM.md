# 추천 알고리즘 총정리

> 뮤징이 "오늘의 곡"을 고르는 전체 과정. 설계 배경은 `RECOMMENDATION.md`,
> 구현 스펙은 `RECOMMENDATION_STAGE2.md`, 이 문서는 **한눈에 보는 요약**이다.

---

## 0. 한 줄 요약

**기분·날씨·계절을 감정 좌표로 바꾸고, 미리 수집·분석해 둔 곡 중에서 그 좌표에 가장 가까운 곡을 고른다.**

```
[배치] 곡 수집 → 감정값 채우기 → tracks에 적재
[요청] 목표 좌표 계산 → DB 조회 → 점수 정렬 → 상위 30곡 중 1곡
```

세 가지 원칙으로 굴러간다.

| 원칙 | 내용 |
|---|---|
| 요청 경로에 외부 API 없음 | iTunes·FreqBlog 호출은 전부 배치 시점으로 밀어냈다. 추천은 DB만 읽는다 |
| 항상 곡을 준다 | 감정값이 없으면 랜덤 폴백. 배치가 안 돌았어도 앱은 동작한다 |
| 곡당 1회만 분석 | 감정값은 변하지 않으므로 영구 캐시. 그래서 무료 티어로 운영된다 |

---

## 1. 배치 ① — 후보 풀 수집

`POST /api/v1/admin/batch/chart` · `POST /api/v1/admin/batch/survey`

### 1.1 아티스트를 모은다

핵심 아이디어는 **차트를 곡 목록이 아니라 아티스트 발굴 목록으로 쓰는 것**이다.
인기 top 100만 담으면 다 아는 노래뿐이라 추천의 의미가 약하다.

| 소스 | 방법 | origin |
|---|---|---|
| **Apple 차트** | `rss.marketingtools.apple.com/api/v2/{kr\|us}/music/most-played/100/songs.json` → `artistId` 추출 | `CHART` |
| **설문** | `"김광석 - 서른 즈음에"` → 파싱 → 곡 검색으로 `artistId` 특정 | `SURVEY` |
| **사용자** | 일기에 곡을 붙이면 자동 유입 | `USER` |

> **설문에서 곡명까지 쓰는 이유** — 가수 이름만으로 검색하면 동명이인이 잡힌다.
> 실측에서 "김광석"이 포크 가수가 아니라 **국악 연주자**로 해석됐다.
> `"김광석 서른 즈음에"`로 곡을 찾아 그 곡의 `artistId`를 쓰면 정확히 좁혀진다.
> 곡명이 없거나 못 찾으면 이름 검색으로 폴백한다.

### 1.2 카탈로그를 펼친다

```
GET itunes.apple.com/lookup?id={artistId}&entity=song&limit=N
```

아티스트 1명 → 곡 N개. 여기서 후보가 수십 배로 불어나고, **대부분이 타이틀곡이 아닌 수록곡**이라
"다 아는 노래" 문제가 풀린다. 응답 첫 항목은 아티스트 정보이므로 걸러낸다.

### 1.3 거른다 — `TrackFilter`

| 제외 조건 | 이유 |
|---|---|
| `previewUrl` 없음 | 감정값 분석도, 앱 내 재생도 불가능 |
| 앨범명에 `DJ Mix` | 클럽 믹스 모음집 |
| 곡명에 `(Live)` `(Inst.)` `(Remix)` `(Remastered)` 등 | 파생 버전. 같은 곡이 여러 개 쌓여 다양성을 깎는다 |

### 1.4 저장

`tracks` 유니크 키는 `(name, artist)`. 있으면 빈 칸만 채우고 없으면 새로 넣는다.
**`origin`은 최초 유입 경로를 보존**한다 — 사용자가 먼저 붙인 곡을 배치가 덮어쓰지 않는다.

| 컬럼 | 채우는 값 |
|---|---|
| `origin` | `CHART` / `SURVEY` / `USER` |
| `is_korean` | 아티스트명에 한글(가~힣) 포함 여부 |
| `genre` | iTunes `primaryGenreName` |
| `valence` 등 | **아직 비어 있음** → 배치 ②가 채운다 |

> ⚠️ iTunes 분당 약 20콜 제한 → 호출 사이 3초 대기. 아티스트 160명이면 8~10분.

---

## 2. 배치 ② — 감정값 채우기

`POST /api/v1/admin/batch/features`

### 2.1 대상은 규칙 한 줄

```sql
SELECT * FROM tracks
 WHERE valence IS NULL AND preview_url IS NOT NULL
 LIMIT 200
```

**출처를 구분하지 않는다.** 차트든 설문이든 사용자가 붙인 곡이든 똑같이 처리된다.
`LIMIT`은 무료 할당량(월 1,000건)을 넘지 않기 위한 실행당 상한이다.

### 2.2 조회

```
GET api.freqblog.com/lookup?track={name}&artist={artist}
    X-Api-Key: {키}
→ { valence, energy, acousticness, bpm, ... 43개 필드 }
```

우리가 쓰는 건 4개다. **`energy`가 우리 좌표계의 arousal(활기)에 대응**한다.

### 2.3 백필 — 카탈로그에 없는 곡

```
1차 조회 → 없음 → FreqBlog이 뒤에서 분석 시작(약 15초) → 우리는 빈손
2차 조회 → 값 수신
```

실측: 1차 `2/17` → 2차 `11/15`. **배치를 두 번 돌려야 대부분 채워진다.**

> ⚠️ 처음 보는 곡은 **할당량 2건**이 든다(주문 + 수령).
> 무료 월 1,000건 기준 신규 곡 **약 500곡**이 한 달 한계.

`429`(할당량·동시성 초과)를 만나면 즉시 중단하고 다음 실행에서 이어간다.

---

## 3. 추천 — `GET /api/v1/recommendations`

### 3.1 목표 감정 좌표

모든 기분과 곡을 **valence(밝기)·arousal(활기) 2축**으로 표현한다.

```
conceptual = 기분 좌표 + 날씨Δ + 계절Δ      (각 축 0~1 클램프)
target     = calibrate(conceptual)          ← 실제 분포로 보정
```

**기분 좌표**

| 기분 | valence | arousal | | 기분 | valence | arousal |
|---|---|---|---|---|---|---|
| 기쁨 | 0.90 | 0.75 | | 답답 | 0.30 | 0.60 |
| 평온 | 0.70 | 0.25 | | 화남 | 0.20 | 0.85 |
| 보통·모름 | 0.50 | 0.50 | | 우울 | 0.20 | 0.30 |
| | | | | 슬픔 | 0.15 | 0.25 |

**날씨 보정**

| 날씨 | Δvalence | Δarousal | | 계절 | 보정 |
|---|---|---|---|---|---|
| 맑음 | +0.10 | +0.05 | | 봄(3~5) | valence +0.05 |
| 바람 | 0.00 | +0.05 | | 여름(6~8) | arousal +0.05 |
| 흐림 | −0.10 | −0.05 | | 가을(9~11) | valence −0.05 |
| 눈 | −0.05 | −0.10 | | 겨울(12~2) | arousal −0.05 |
| 비 | −0.15 | −0.10 | | | |

### 3.2 좌표 보정 — `EmotionCalibrator`

기분 좌표는 0~1 전체를 쓴다는 전제로 정했는데, **실제 모델이 내놓는 valence는 훨씬 좁다.**
실측에서 김광석의 어두운 포크 곡도 0.34~0.46이었고 최솟값이 0.3 근처였다.

```
개념 좌표:  슬픔 0.15 ── 우울 0.20 ──────────── 기쁨 0.90
실제 곡들:              [0.30 ═══════════ 0.75]
            ↑ 이 구간엔 곡이 아예 없다
```

그대로 두면 슬픔·우울이 똑같이 "가장 어두운 곡"을 고르게 되므로, 목표 좌표를 관측 구간으로 **선형 재매핑**한다.

```
valence: 0~1 → 0.30 ~ 0.75      슬픔 0.15 → 0.3675
arousal: 0~1 → 0.10 ~ 0.95      기쁨 0.90 → 0.7050
```

보정 후 8개 기분 위치:

| 기분 | 보정 좌표 | | 기분 | 보정 좌표 |
|---|---|---|---|---|
| 기쁨 | (0.705, 0.737) | | 화남 | (0.390, 0.822) |
| 평온 | (0.615, 0.312) | | 우울 | (0.390, 0.355) |
| 보통 | (0.525, 0.525) | | 슬픔 | (0.367, 0.312) |
| 답답 | (0.435, 0.610) | | | |

### 3.3 후보 조회 — 외부 호출 없음

```
findScorable()    valence·energy 있는 곡
  └ 비었으면 findPlayable()   previewUrl 있는 곡 전체  ← 랜덤 폴백
    └ 그것도 비었으면 → track: null
```

### 3.4 최근 추천 곡 제외

`diary_tracks(role=RECOMMENDED)` 중 **최근 14일** 내 곡을 뺀다.
전부 빠져버리면 제외를 포기한다 — 곡은 반드시 줘야 하므로.

### 3.5 점수 계산 — 낮을수록 좋다

```
score = 1.0 × (곡.valence − target.valence)²
      + 1.0 × (곡.energy  − target.arousal)²
      + [날씨가 비] 0.5 × (1 − 곡.acousticness)²
      − 취향·한국곡 가산점
```

| 가산점 | 값 | 비고 |
|---|---|---|
| seed 아티스트 일치 | −0.15 | seed는 후보를 **좁히지 않고** 점수만 깎는다(취향 감옥 방지) |
| seed 장르 일치 | −0.05 | |
| 한국 곡 | −0.03 | 동점 처리용. 0.12는 감정 매칭을 압도해서 낮췄다 |

> 가산점 `b`는 "한 축에서 `√b` 만큼 벗어나도 이긴다"는 뜻이다.
> 0.12면 35% 벗어난 한국 곡이 좌표가 정확한 해외 곡을 이겨버린다.

### 3.6 선택 — 같은 조건이면 같은 곡

```
seed  = FNV-1a("userId|date|mood|weather")
index = seed mod min(30, 후보수)
```

- 같은 사람 · 같은 날 · **같은 조건** → 항상 같은 곡
- 기분이나 날씨를 **바꾸면** → 다른 곡
- **DB 저장이 필요 없다.** 추천 API는 무상태로 유지된다

> `Objects.hash()`를 쓰지 않은 이유: **enum의 hashCode는 객체 주소 기반이라 JVM을 재시작하면 값이 바뀐다.**
> 서버를 껐다 켤 때마다 같은 조건인데 다른 곡이 나오게 된다. FNV-1a는 어디서 돌리든 같은 값을 낸다.

> **K를 30으로 잡은 이유**: 같은 계절에 같은 기분·날씨가 반복되면 상위 목록도 같아진다.
> K가 작으면 그 몇 곡만 돌려막게 되므로 넓게 잡아 한 달 이상 반복돼도 곡이 겹치지 않게 한다.

---

## 4. 알려진 한계

| # | 한계 | 대응 |
|---|---|---|
| 1 | **valence 정확도가 낮다** (연구 기준 R² 0.3~0.5) — 가사를 못 들어서 "신나는 반주 + 이별 가사"를 밝은 곡으로 본다 | 순서만 필요하므로 극단은 쓸 만하다. 2차 설문으로 검증 |
| 2 | **슬픔(0.367, 0.312) ↔ 우울(0.390, 0.355) 거리 0.048** — 사실상 같은 추천이 나온다 | 좌표 자체를 벌려야 한다 |
| 3 | 보정 구간 0.30~0.75는 **곡 수십 개로 잰 잠정치** | 풀이 커지면 `MIN/MAX(valence)` 재측정 후 설정 갱신 |
| 4 | `Jay-Z` 같은 하이픈 이름은 파싱이 잘못 나뉜다 | 이름 검색 폴백으로 대개 복구. 국내 설문에선 드물어 감수 |
| 5 | **기분 좌표 8개는 전부 추측값** | 기분별 설문(§`SURVEY_DRAFT.md` §11)으로 측정값 교체 예정 |
| 6 | arousal이 1.00에 닿는 곡이 있다(포화 가능성) | 분포를 더 모아 확인 |

---

## 5. 설정 한눈에

```yaml
musing:
  chart:
    enabled: false            # 자동 실행 스위치(수동 트리거는 항상 가능)
    storefronts: kr,us
    limit: 100                # Apple RSS 상한
    songs-per-artist: 50      # 풀 크기·소요 시간 조절
    request-delay-ms: 3000    # iTunes 분당 20콜 대응
  freqblog:
    api-key: ${FREQBLOG_API_KEY:}   # 커밋 금지
    max-per-run: 200          # 무료 월 1,000건 보호
  recommendation:
    calibrate: true           # 좌표 보정 on/off
    valence-min: 0.30
    valence-max: 0.75
    arousal-min: 0.10
    arousal-max: 0.95
    weight-valence: 1.0
    weight-arousal: 1.0
    weight-acoustic-rain: 0.5
    bonus-same-artist: 0.15
    bonus-same-genre: 0.05
    bonus-korean: 0.03
    top-k: 30
    exclude-recent-days: 14
```

---

## 6. 관련 파일

| 역할 | 클래스 |
|---|---|
| 기분·날씨·계절 → 좌표 | `MoodWeatherSeasonMapper` |
| 좌표를 실제 분포로 보정 | `EmotionCalibrator` |
| 점수 계산 | `TrackScorer`, `ScoringWeights` |
| 같은 조건 = 같은 곡 | `DeterministicPicker` |
| 추천 본체 | `RecommendationService` |
| 차트 수집 | `AppleChartClient`, `ChartCollectService` |
| 설문 수집 | `SurveyEntry`, `SurveyCollectService` |
| 후보 필터 | `TrackFilter` |
| 감정값 조회 | `FreqBlogClient`, `FeatureFillService` |
| 저장 | `TrackUpsertService`, `TrackFeatureWriter` |
