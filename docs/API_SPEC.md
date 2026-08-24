# 뮤징(musing) — API 명세서

Spring Boot 백엔드(`musing_BE`)용 REST API 명세. 프론트(Flutter) 핸드오프 문서 기준.

- **Base URL**: `https://api.musing.app` (로컬: `http://localhost:8080`)
- **버전 프리픽스**: `/api/v1`
- **인코딩**: `application/json; charset=utf-8`
- **인증**: `Authorization: Bearer <accessToken>` (JWT). 로그인·검색·추천 프록시 외 대부분 인증 필요.
- **날짜 형식**: `date`는 `YYYY-MM-DD`, 타임스탬프는 ISO-8601 `YYYY-MM-DDTHH:mm:ssZ`.

---

## 0. 공통 규약

### 0.1 공통 응답 래퍼

```jsonc
// 성공
{ "success": true, "data": { /* 리소스 */ } }
// 실패
{ "success": false, "error": { "code": "DIARY_NOT_FOUND", "message": "해당 날짜의 일기가 없습니다." } }
```

### 0.2 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 조회/수정 성공 |
| 201 | 생성 성공 |
| 204 | 삭제 성공(본문 없음) |
| 400 | 잘못된 요청(검증 실패) |
| 401 | 인증 실패/토큰 만료 |
| 403 | 권한 없음(타인 리소스) |
| 404 | 리소스 없음 |
| 409 | 충돌(같은 날짜 일기 중복 등) |
| 500 | 서버 오류 |

### 0.3 공통 에러 코드

`VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `UNSUPPORTED_PROVIDER`, `INVALID_SOCIAL_TOKEN`, `INVALID_REFRESH_TOKEN`, `USER_NOT_FOUND`, `DIARY_NOT_FOUND`, `DIARY_ALREADY_EXISTS`, `FUTURE_DATE_NOT_ALLOWED`, `DATA_CONFLICT`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_ERROR`, `EXTERNAL_API_TIMEOUT`, `EXTERNAL_API_UNAVAILABLE`, `INTERNAL_ERROR`.

> `DATA_CONFLICT`(409)는 일기 중복 외의 DB 제약 충돌입니다. 대개 같은 요청이 동시에 두 번
> 들어온 경우이므로, 클라이언트는 한 번 재시도하면 됩니다.

### 0.4 고정값(enum)

| 항목 | 허용값 |
|---|---|
| `mood` | `기쁨` `우울` `슬픔` `화남` `평온` `보통` `답답` `모름` |
| `weather` | `맑음` `흐림` `비` `눈` `바람` |
| `provider` | `kakao` `naver` `google` `apple` |
| `track role` | `MY` `RECOMMENDED` |

> `mood`/`weather`는 **한글 문자열 그대로** 주고받습니다(추천 태그 매핑 키).

### 0.5 공통 모델 — TrackInfo

```jsonc
{
  "id": 12,                  // 서버 저장 후 부여(검색 결과 단계에선 null 가능)
  "name": "밤편지",           // 필수, 최대 255자
  "artist": "아이유",         // 필수, 최대 255자
  "album": "밤편지",          // nullable, 최대 255자
  "artworkUrl": "https://...600x600.jpg", // nullable, 최대 500자
  "previewUrl": "https://...preview.m4a"  // nullable, 최대 500자
}
```

> 길이 제한은 DB 컬럼과 같습니다. 넘으면 저장 전에 `VALIDATION_ERROR`(400)로 거절합니다.

---

## 1. 인증 (Auth)

### 1.1 소셜 로그인
`POST /api/v1/auth/login`  · 인증 불필요

프론트가 소셜 SDK로 받은 자격증명을 서버에 전달 → 서버가 검증 후 자체 JWT 발급.

**Request**
```jsonc
{
  "provider": "kakao",        // kakao | naver | google | apple
  "idToken": "<제공자별 자격증명>",
  "state": "<네이버 전용>",     // 네이버만 필수, 그 외 제공자는 생략
  "nickname": "예성"           // 최초 가입 시 선택(없으면 소셜 프로필 사용)
}
```

**⚠️ `idToken`에 넣을 값은 제공자마다 다릅니다.**

| provider | `idToken`에 넣을 값 | `state` | 서버가 검증하는 것 |
|---|---|---|---|
| `google` | id_token | 불필요 | 토큰 서명 + `aud` = 우리 client id |
| `apple` | id_token | 불필요 | 토큰 서명 + `aud` = 우리 client id |
| `kakao` | id_token (OIDC) | 불필요 | 토큰 서명 + `aud` = 우리 앱 키 |
| `naver` | **인가 코드(authorization code)** | **필수** | 우리 client_id/secret으로 코드를 직접 교환 |

> **서버에 키가 설정되지 않은 제공자로 로그인하면 `UNSUPPORTED_PROVIDER`(400)가 나갑니다.**
> 제공자를 순차적으로 붙이는 중이라 그렇습니다. 프론트는 이 코드를 "해당 로그인 수단 사용 불가"로
> 처리하면 됩니다.

> **카카오는 OIDC id_token입니다** — access_token이 아닙니다. Flutter SDK의 `OAuthToken.idToken`을
> 그대로 보내면 됩니다. 이걸 쓰려면 Kakao Developers 콘솔에서 **OpenID Connect 활성화**가 필요합니다.

> **네이버만 인가 코드인 이유:** 네이버는 OIDC를 제공하지 않고, "이 토큰이 우리 앱에 발급된
> 것인가"를 확인해 주는 API도 없습니다. 액세스 토큰을 그대로 받으면 제3자가 자기 앱으로 모은
> 토큰을 우리 로그인에 던져 남의 계정이 될 수 있습니다(토큰 치환). 인가 코드를 받아 서버가
> client_secret으로 직접 교환하면, 교환에 성공했다는 사실 자체가 우리 앱 발급 증거가 됩니다.
> 프론트는 네이버 로그인 콜백에서 받은 `code`와 `state`를 그대로 보내면 됩니다.

**Response 200**
```jsonc
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { "id": 1, "email": "user@kakao.com", "nickname": "예성", "provider": "kakao" },
    "isNewUser": true
  }
}
```

### 1.2 토큰 재발급
`POST /api/v1/auth/refresh`  · 인증 불필요

리프레시 토큰은 **1회용**입니다. 재발급할 때마다 회전하며, 쓴 토큰을 다시 보내면
`INVALID_REFRESH_TOKEN`(401)입니다. 또 한 사용자당 활성 세션은 **최대 5개**로,
6번째 로그인부터 가장 오래된 세션이 밀려나 로그아웃됩니다.

```jsonc
// Request
{ "refreshToken": "eyJhbGc..." }
// Response 200
{ "success": true, "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 3600 } }
```

### 1.3 내 정보
`GET /api/v1/auth/me`  · 인증 필요

```jsonc
{ "success": true, "data": { "id": 1, "email": "user@kakao.com", "nickname": "예성", "provider": "kakao", "createdAt": "2026-07-01T09:00:00Z" } }
```

### 1.4 로그아웃
`POST /api/v1/auth/logout`  · 인증 필요 — refreshToken 무효화. **204**.

```jsonc
// Request (바디 전체가 선택)
{ "refreshToken": "eyJhbGc..." }   // 주면 이 기기만 로그아웃
// 바디를 생략하면 이 사용자의 모든 기기에서 로그아웃
```

### 1.5 회원 탈퇴 (계정 삭제)
`DELETE /api/v1/auth/withdraw`  · 인증 필요 — 사용자 계정과 **모든 개인 데이터**(일기·곡 연결·리프레시 토큰)를 삭제. FK `ON DELETE CASCADE`로 하위 일괄 정리. **204**.
> 🍎 **App Store 5.1.1**: 계정 생성을 지원하는 앱은 **앱 내 계정 삭제**를 반드시 제공(비활성화만으론 리젝). 삭제는 앱에서 쉽게 찾을 수 있어야 하며 개인 데이터까지 지워야 함.

---

## 2. 일기 (Diary)

### 2.1 월별 기록 조회 (캘린더용)
`GET /api/v1/diaries?month=YYYY-MM`  · 인증 필요

`calendar_screen.dart`의 `_diaryDays`/`_musicDays` 더미를 대체.

**Response 200**
```jsonc
{
  "success": true,
  "data": {
    "month": "2026-08",
    "days": [
      {
        "date": "2026-08-02",
        "hasDiary": true,
        "hasMusic": true,
        "coverArtworkUrl": "https://...600x600.jpg", // MY 우선, 없으면 RECOMMENDED, 둘 다 없으면 null
        "mood": "기쁨"
      },
      { "date": "2026-08-03", "hasDiary": false, "hasMusic": true, "coverArtworkUrl": "https://...", "mood": null }
    ]
  }
}
```
> `days`는 기록이 있는 날만 반환(빈 날 생략). 프론트에서 Set으로 변환해 사용.

### 2.2 특정 날짜 일기 상세
`GET /api/v1/diaries/{date}`  · 인증 필요 · `date = YYYY-MM-DD`

**Response 200**
```jsonc
{
  "success": true,
  "data": {
    "id": 101,
    "date": "2026-08-02",
    "title": "여름의 끝",
    "body": "오늘은 바람이 선선했다.",
    "mood": "평온",
    "weather": "바람",
    "myTrack": { "id": 12, "name": "밤편지", "artist": "아이유", "album": "밤편지", "artworkUrl": "...", "previewUrl": "..." },
    "todayTrack": { "id": 33, "name": "Through the Night", "artist": "IU", "album": "...", "artworkUrl": "...", "previewUrl": "..." },
    "createdAt": "2026-08-02T21:10:00Z",
    "updatedAt": "2026-08-02T21:10:00Z"
  }
}
```
없으면 **404** `DIARY_NOT_FOUND`.

### 2.3 일기 작성
`POST /api/v1/diaries`  · 인증 필요

**Request**
```jsonc
{
  "date": "2026-08-02",       // 필수
  "title": "여름의 끝",         // 선택
  "body": "오늘은 바람이...",    // 선택
  "mood": "평온",              // 필수 (8종)
  "weather": "바람",           // 필수 (5종)
  "myTrack": {                // 선택 — 사용자가 고른 곡
    "name": "밤편지", "artist": "아이유", "album": "밤편지",
    "artworkUrl": "...", "previewUrl": "..."
  },
  "todayTrack": { "name": "...", "artist": "..." } // 선택 — 추천 결과를 함께 저장
}
```
**Response 201** — 2.2와 동일한 상세 객체. 같은 날짜 존재 시 **409** `DIARY_ALREADY_EXISTS`.

### 2.4 일기 수정
`PUT /api/v1/diaries/{date}`  · 인증 필요 — 2.3과 동일 바디(부분 수정 시 PATCH 아닌 전체 교체). **200** 상세 반환.

### 2.5 일기 삭제
`DELETE /api/v1/diaries/{date}`  · 인증 필요 — **204**. 연결된 `diary_track`도 함께 삭제.

---

## 3. 추천 (Recommendation)

### 3.1 추천곡 요청
`GET /api/v1/recommendations`  · 인증 필요

핸드오프 §5의 클라이언트 추천 로직을 서버로 이전 + Last.fm 의존 제거(상업 이용 불가). **감정 좌표(valence-arousal) 기반 매칭**으로 재설계. 알고리즘 상세는 `RECOMMENDATION.md`.

> 🔒 **프라이버시**: 입력은 아래 정형 값(mood·weather·date·seed)뿐이며 **일기 제목·본문은 전송하지 않는다.**

**Query**
| 파라미터 | 필수 | 설명 |
|---|---|---|
| `mood` | ✅ | 8종 중 하나 |
| `weather` | ✅ | 5종 중 하나 |
| `date` | ✅ | `YYYY-MM-DD` (계절 판별용) |
| `seedName` | ❌ | 나의 음악 곡명 |
| `seedArtist` | ❌ | 나의 음악 가수(국내/해외 판별 + 유사 아티스트 풀) |

**Response 200**
```jsonc
{ "success": true, "data": { "track": { "name": "...", "artist": "...", "album": "...", "artworkUrl": "...", "previewUrl": "..." } } }
```
조건 불충족(후보 없음)이면 **200** + `data.track = null` 또는 **404** `RECOMMENDATION_EMPTY`. 외부 API 장애 시 **502** `EXTERNAL_API_ERROR`.

> 서버 로직: ①국내/해외 판별(seedArtist 한글 여부 → kr/us) → ②기분·날씨·계절 → 목표 감정 좌표 + 후보 장르 → ③후보 수집(Apple Music RSS 장르 차트 + seed 아티스트/장르 via iTunes) → ④감정 좌표 거리 랭킹 + 캐스케이드(취향→기분→날씨→계절), 상위 K 중 랜덤 → ⑤iTunes 커버 보강(최대 5회 재추첨). 곡 감정값은 ReccoBeats에서 조회해 `tracks`에 캐싱.

---

## 4. 음악 검색 (Track Search)

### 4.1 곡 검색 (iTunes 프록시)
`GET /api/v1/tracks/search?q={검색어}&limit=20`  · 인증 필요

| 파라미터 | 제약 |
|---|---|
| `q` | 필수, 공백만 있으면 안 됨, 최대 100자 — 위반 시 `VALIDATION_ERROR`(400) |
| `limit` | 선택(기본 20). 1~50 범위를 벗어나면 서버가 잘라서 처리 |

**Response 200**
```jsonc
{
  "success": true,
  "data": {
    "query": "아이유",
    "results": [
      { "name": "밤편지", "artist": "아이유", "album": "밤편지", "artworkUrl": "...", "previewUrl": "..." }
    ]
  }
}
```
> 검색 단계 결과는 아직 DB 미저장이라 `id` 없음. 일기 저장 시 서버가 `track`을 upsert.
> 동일 사용자/검색어/limit 조합은 짧은 TTL 동안 캐시를 재사용한다. 외부 API 장애 시 캐시가 있으면 stale 데이터를 반환해 지연 전파를 줄인다.
>
> **오류 응답**
> - `401 UNAUTHORIZED`: 인증 없음/토큰 무효
> - `429 RATE_LIMIT_EXCEEDED`: 사용자/IP 기준 요청 한도 초과
> - `502 EXTERNAL_API_ERROR`: 외부 API 일반 실패
> - `503 EXTERNAL_API_UNAVAILABLE`: 외부 API 장애 감지(회로 차단/동시호출 제한)
> - `504 EXTERNAL_API_TIMEOUT`: 외부 API 응답 지연

---

## 5. 통계 (Stats)

### 5.1 홈 통계
`GET /api/v1/stats`  · 인증 필요 — 홈 화면의 하드코딩 수치(10/8/3 등) 대체.

**Response 200**
```jsonc
{
  "success": true,
  "data": {
    "totalDiaries": 10,       // 전체 일기 수
    "totalMusic": 8,          // 음악 등록된 일기 수
    "currentStreak": 3,       // 연속 작성 일수
    "todayWritten": false,    // 오늘 일기 작성 여부
    "topMood": "평온"          // 최다 기분(선택)
  }
}
```

정책:
- 집계 범위는 **전체 기간**(all-time)이며 `month` 쿼리 파라미터는 받지 않는다.
- `todayWritten` 및 `currentStreak`의 날짜 기준은 **KST(Asia/Seoul)** 이다.
- `currentStreak`는 최신 작성일이 오늘 또는 어제일 때만 시작되며, 월 경계를 넘어도 날짜가 연속이면 이어서 계산한다.
- `topMood`는 일기가 없으면 `null` 이고, 최다 빈도 동률이면 기분 코드(한글) **가나다 오름차순**으로 1개를 선택한다.

---

> **홈 화면 앨범 커버 출처**: 메인 화면의 "오늘의 곡" 커버는 `GET /diaries/{today}` 응답의 `myTrack.artworkUrl`(없으면 `todayTrack.artworkUrl`)을 사용한다. 오늘 일기가 없으면 404 → 빈 상태로 표시. 캘린더 커버는 `GET /diaries?month=`의 `coverArtworkUrl`. 커버 원본은 모두 `tracks.artwork_url`.

## 6. 구현 권장 순서

1. **일기 CRUD**(2장) — 인증 임시 우회로 먼저 붙여 프론트 더미를 실데이터로 교체 → 캘린더·통계 즉시 활성화
2. **JWT + 소셜 로그인**(1장) — 이후 모든 엔드포인트에 인증 적용
3. **추천 로직 서버 이전**(3장) — Last.fm 키 노출 문제 동시 해결
4. **검색·통계**(4·5장) — 프록시/집계 마무리

---

## 7. 앱스토어(iOS) 배포 요구사항 대응

| 요구사항 | 규정 | 대응 |
|---|---|---|
| 앱 내 계정 삭제 | Guideline 5.1.1 | `DELETE /auth/withdraw` (§1.5) + CASCADE 삭제 ✅ |
| Sign in with Apple | Guideline 4.8 | 카카오·네이버·구글 제공 → **Apple 로그인 필수 포함**. provider에 apple 있음. UI 동등 노출 ✅ |
| 개인정보 처리방침 + App Privacy 라벨 | 필수 | 일기 텍스트 외부 미전송 → 수집 최소화로 유리 |
| 음악 미리듣기·커버 사용 | iTunes 약관 | 미리듣기/커버 옆 **Apple Music 스토어 링크** 표기 |
