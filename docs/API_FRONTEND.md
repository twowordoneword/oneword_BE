# 뮤징 — 프론트 연동 API (구현 완료분: 일기 CRUD)

> 지금 **실제로 동작하는** 엔드포인트만 정리. (전체 계획은 `API_SPEC.md`)
> Base URL: `http://localhost:8080` · 공통 프리픽스 `/api/v1`
> 인코딩: `application/json; charset=utf-8`

## 공통 규약

**응답은 항상 이 래퍼로 감싸집니다.**

```jsonc
// 성공
{ "success": true, "data": { /* 실제 데이터 */ } }
// 실패
{ "success": false, "error": { "code": "DIARY_NOT_FOUND", "message": "해당 날짜의 일기가 없습니다." } }
```

- 날짜: `date`는 `YYYY-MM-DD`, 타임스탬프는 ISO-8601(`2026-08-02T21:10:00`)
- **인증: 아직 없음.** 현재 개발용 고정 사용자로 동작하므로 토큰 불필요. (인증 붙으면 `Authorization: Bearer <token>` 추가 예정)
- 고정값(그대로 전송):
  - `mood`: `기쁨` `우울` `슬픔` `화남` `평온` `보통` `답답` `모름`
  - `weather`: `맑음` `흐림` `비` `눈` `바람`

**TrackInfo 모델** (myTrack / todayTrack)

```jsonc
{
  "id": 12,            // 저장 후 서버가 부여 (요청 시엔 없어도 됨)
  "name": "밤편지",     // 필수
  "artist": "아이유",   // 필수
  "album": "밤편지",    // 선택
  "artworkUrl": "https://.../600x600.jpg",  // 선택
  "previewUrl": "https://.../preview.m4a"   // 선택
}
```

---

## 1. 월별 기록 조회 (캘린더)

`GET /api/v1/diaries?month=YYYY-MM`

기록 있는 날만 반환. 프론트에서 Set으로 변환해 캘린더 점 표시.

**응답 200**
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
        "coverArtworkUrl": "https://.../600x600.jpg",  // MY 우선, 없으면 추천곡, 둘 다 없으면 null
        "mood": "평온"
      }
    ]
  }
}
```

---

## 2. 특정 날짜 일기 상세

`GET /api/v1/diaries/{date}`   예: `/api/v1/diaries/2026-08-02`

**응답 200**
```jsonc
{
  "success": true,
  "data": {
    "id": 101,
    "date": "2026-08-02",
    "title": "여름의 끝",
    "body": "바람이 선선했다",
    "mood": "평온",
    "weather": "바람",
    "myTrack":  { "id": 12, "name": "밤편지", "artist": "아이유", "album": null, "artworkUrl": null, "previewUrl": null },
    "todayTrack": null,
    "createdAt": "2026-08-02T21:10:00",
    "updatedAt": "2026-08-02T21:10:00"
  }
}
```
없으면 **404** `DIARY_NOT_FOUND`.

---

## 3. 일기 작성

`POST /api/v1/diaries`

**요청 바디**
```jsonc
{
  "date": "2026-08-02",     // 필수
  "title": "여름의 끝",       // 선택
  "body": "바람이 선선했다",   // 선택
  "mood": "평온",            // 필수
  "weather": "바람",         // 필수
  "myTrack":  { "name": "밤편지", "artist": "아이유" },  // 선택 (곡 없이 저장 가능)
  "todayTrack": null                                    // 선택 (추천곡)
}
```
**응답 201** — 2번과 동일한 상세 객체.
같은 날짜 존재 시 **409** `DIARY_ALREADY_EXISTS`.

---

## 4. 일기 수정 (전체 교체)

`PUT /api/v1/diaries/{date}`

요청 바디는 3번과 동일. 보낸 내용으로 **통째로 교체**(부분 수정 아님, 곡도 재설정). **응답 200** 상세 객체.
없으면 **404** `DIARY_NOT_FOUND`.

---

## 5. 일기 삭제

`DELETE /api/v1/diaries/{date}`

**응답 204** (본문 없음). 연결된 곡도 함께 정리. 없으면 **404** `DIARY_NOT_FOUND`.

---

## 에러 코드

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수값 누락/잘못된 mood·weather |
| 404 | `DIARY_NOT_FOUND` | 해당 날짜 일기 없음 |
| 409 | `DIARY_ALREADY_EXISTS` | 같은 날짜에 이미 일기 있음 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

## Flutter 연동 팁

- 응답 파싱: 먼저 `success` 확인 → true면 `data`, false면 `error.code`로 분기.
- `mood`/`weather`는 프론트의 고정 문자열과 **그대로** 일치(변환 불필요).
- 캘린더: `days`를 돌며 `hasDiary`/`hasMusic`으로 점 표시, `coverArtworkUrl`로 앨범 커버.
- 작성 화면 저장 버튼: 이미 있는 날짜면 409가 오므로, 그 경우 **수정(PUT)** 흐름으로 전환.
