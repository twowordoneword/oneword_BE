# 한마디(뮤징) — 프론트엔드 핸드오프 문서

> 백엔드(Spring Boot, `oneword_BE`) 개발용. Flutter 프론트가 현재 어떤 데이터를 다루고, 무엇이 가짜(더미)이며, 서버가 무엇을 채워줘야 하는지 정리한 문서입니다.

## 1. 앱 한 줄 요약

하루를 한 줄 일기로 기록하면, 그날의 **기분·날씨·계절·음악 취향**을 조합해 어울리는 노래 한 곡을 추천해주는 음악 일기 앱.
현재 프론트는 UI/추천 로직까지 완성돼 있으나 **데이터가 전부 휘발성(메모리)·더미**라 서버가 필요한 단계.

## 2. 화면 구조

| 화면 | 파일 | 역할 | 서버 연동 지점 |
|---|---|---|---|
| 스플래시 | `splash_screen.dart` | 원고지 인트로 → 앱 진입 | (로그인 자리) |
| 홈 | `home_screen.dart` | 일기 쓰기 / 캘린더 진입 / 오늘의 곡 미리보기 | 오늘 일기 존재 여부, 통계 |
| 일기 | `diary_screen.dart` | 날짜별 일기 작성 + 나의 음악 선택 + 오늘의 추천 | **일기 CRUD, 음악 저장, 추천 요청** |
| 캘린더 | `calendar_screen.dart` | 일기·음악 등록일 표시, 날짜별 앨범 커버 | **월별 기록 조회** |

메인 진입: `main.dart` → `SplashScreen`. 앱 이름 `뮤징`(musing), 시드 컬러 `#EE6C5A`, 배경 `#FCF9F2`, 폰트 DungGeunMo.

## 3. 핵심 데이터 모델 (프론트가 실제로 다루는 필드)

### 3.1 일기 (Diary) — `diary_screen.dart` 상태값
- `date` : 일기 날짜 (DateTime, 화면 생성 시 주입)
- `title` : 제목 (`_titleCtrl`)
- `body` : 본문 (`_bodyCtrl`)
- `mood` : 기분 — **8종 고정값**: `기쁨 / 우울 / 슬픔 / 화남 / 평온 / 보통 / 답답 / 모름`
- `weather` : 날씨 — **5종 고정값**: `맑음 / 흐림 / 비 / 눈 / 바람`
- `myTrack` : 나의 음악 (사용자가 고른 곡, `TrackInfo`)
- `todayTrack` : 오늘의 추천곡 (서버/추천 결과, `TrackInfo`)
- `saved` : 저장 여부 (true면 저장/수정 모드)

> ⚠️ mood·weather는 추천 태그 매핑의 키로 그대로 쓰이므로(§5), **enum/코드값을 프론트 문자열과 반드시 일치**시켜야 합니다.

### 3.2 음악 (Track) — `TrackInfo` (`music_service.dart`)
```
TrackInfo {
  name        // 곡명 (필수)
  artist      // 가수 (필수)
  album?      // 앨범명
  artworkUrl? // 앨범 커버 (iTunes 600x600)
  previewUrl? // iTunes 30초 미리듣기
}
```
곡의 원천은 iTunes Search / Last.fm / Apple Music RSS. 서버는 이 5개 필드를 그대로 저장·반환하면 됩니다.

## 4. 지금 "가짜"라서 서버가 채워야 하는 것

1. **일기 저장** — 작성해도 화면 벗어나면 사라짐. → `일기 CRUD` 필요
2. **캘린더 기록** (`calendar_screen.dart`)
   - `_diaryDays = {2,5,8,12,...}`, `_musicDays = {3,7,12,...}` → **하드코딩 더미**
   - 8월 미리보기 커버도 iTunes에서 즉석 생성한 더미
   - → `GET /diaries?month=` 로 실데이터 대체 필요
3. **통계(홈의 10/8/3 등)** — 하드코딩. → 서버 집계 필요
4. **로그인** — 화면만 있고 인증 없음. → JWT + 소셜 로그인
5. **추천 로직** — 현재 클라이언트에서 외부 API 직접 호출(§5). Last.fm 키가 코드에 노출(`a43e0c...`)돼 있어 **서버 이전 권장**.

## 5. 추천 로직 (서버 이전 대상 — 그대로 옮기면 됨)

`MusicService.getRecommendation({mood, weather, seedTrack, date})` 흐름:

1. **국내/해외 판별** — `seedTrack.artist`에 한글이 있으면 국내, 없으면 해외(팝) 태그 세트 사용
2. **태그 매핑** — mood·weather를 Last.fm 태그로 변환 (예: 기쁨→`k-pop`/`pop`, 비→`k-ballad`/`sad`). 계절은 일기 날짜의 월로 결정(봄=맑음, 여름=바람, 가을=흐림, 겨울=눈 태그 재사용)
3. **후보 수집** (병렬)
   - Last.fm `tag.getTopTracks` (mood/weather/season, 페이지 랜덤 샘플링으로 유명곡 쏠림 완화)
   - Apple Music RSS 실시간 차트 (최신곡 보충, kr/us)
   - seedTrack 있으면 Last.fm `artist.getSimilar` + `artist.getTopTracks`로 장르 풀
4. **우선순위 캐스케이드**: `장르(취향) → 기분 → 날씨 → 계절`. 각 단계는 후보가 남을 때만 좁힘(비면 이전 단계 유지)
5. **메타 보강** — 선정곡을 iTunes에서 검색해 앨범 커버 부착. 곡명·가수 느슨한 매칭 실패 시 잘못된 커버 대신 미표시(최대 5회 재추첨)

**입력**: mood, weather (둘 다 필수), 선택적 seedTrack(나의 음악), date
**출력**: `TrackInfo?` (조건 불충족 시 null)

> 외부 API: Last.fm(`ws.audioscrobbler.com`), iTunes Search(`itunes.apple.com`), Apple Music RSS(`rss.marketingtools.apple.com`). 서버 이전 시 API 키는 서버 환경변수로.

## 6. 제안 백엔드 스펙 (초안)

### ERD
- **User** (id, email, nickname, provider, provider_id, created_at)
- **Diary** (id, user_id FK, date, title, body, mood, weather, created_at, updated_at) — user_id+date 유니크
- **Track** (id, name, artist, album, artwork_url, preview_url) — 또는 Diary에 임베드
- **DiaryTrack** (diary_id, track_id, role[`MY`|`RECOMMENDED`]) — 일기당 나의음악/추천곡 연결

### 엔드포인트 초안
```
POST   /auth/login            # 소셜 로그인 → JWT
GET    /diaries?month=YYYY-MM # 캘린더용 월별 기록 (일기일/음악일/커버)
GET    /diaries/{date}        # 특정 날짜 일기 상세
POST   /diaries               # 일기 작성 (title, body, mood, weather, myTrack)
PUT    /diaries/{date}        # 수정
DELETE /diaries/{date}
GET    /recommendations       # mood, weather, seedTrack, date → 추천곡
GET    /tracks/search?q=      # 음악 검색 (iTunes 프록시)
GET    /stats                 # 홈 통계 (일기 수, 음악 수 등)
```

### 추천 시작 권장 순서
1. 일기 CRUD (인증 없이 먼저) → 프론트 더미를 API로 교체하면 캘린더·통계가 살아남
2. JWT + 소셜 로그인
3. 추천 로직 서버 이전 (Last.fm 키 문제 동시 해결)

## 7. 기술 스택 (프론트)
Flutter(Dart), Material 3, `http`, `audioplayers`(미리듣기). 폰트 DungGeunMo/TossFaceFont.
Flutter 저장소: 이 프로젝트 / Backend: `oneword_BE` (Spring Boot 예정).
