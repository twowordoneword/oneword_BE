-- 뮤징(musing) 스키마 — MySQL 8 / MariaDB 10.6+
-- charset: utf8mb4 (한글 mood/weather 저장)

CREATE DATABASE IF NOT EXISTS musing
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE musing;

-- ------------------------------------------------------------------
-- USER
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50)  NOT NULL,
    provider          VARCHAR(20)  NOT NULL,           -- kakao | naver | google | apple
    provider_id       VARCHAR(255) NULL,               -- 소셜 고유 ID
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_provider (provider, provider_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- REFRESH_TOKEN (기기별 세션 — 로그아웃/무효화용)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,   -- 리프레시 토큰 원문이 아닌 해시 저장(유출 대비)
    expires_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token (token_hash),
    KEY idx_refresh_user (user_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- TRACK (곡 마스터 — 중복 저장 방지)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tracks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    artist      VARCHAR(255) NOT NULL,
    isrc        VARCHAR(15)  NULL,               -- 국제 표준 녹음 코드(곡 식별·중복제거). 있으면 이걸로 매칭
    album       VARCHAR(255) NULL,
    artwork_url VARCHAR(500) NULL,
    preview_url VARCHAR(500) NULL,
    -- 감정 특성 캐시 (ReccoBeats/AcousticBrainz, 추천 랭킹용) — 한번 조회 후 재사용
    valence      DECIMAL(4,3) NULL,   -- 0(어두움)~1(밝음)
    energy       DECIMAL(4,3) NULL,   -- 0(차분)~1(격렬), arousal 근사
    acousticness DECIMAL(4,3) NULL,   -- 0~1
    tempo        DECIMAL(6,2) NULL,   -- BPM
    features_source      VARCHAR(20) NULL,  -- reccobeats | acousticbrainz
    features_fetched_at  DATETIME    NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tracks_isrc (isrc),            -- NULL 다중 허용, 실제 ISRC는 중복 불가
    UNIQUE KEY uq_tracks_name_artist (name, artist)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- DIARY (하루 1개 — user_id + diary_date 유니크)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diaries (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    diary_date DATE         NOT NULL,
    title      VARCHAR(255) NULL,
    body       TEXT         NULL,
    mood       VARCHAR(10)  NOT NULL,   -- 기쁨|우울|슬픔|화남|평온|보통|답답|모름
    weather    VARCHAR(10)  NOT NULL,   -- 맑음|흐림|비|눈|바람
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diaries_user_date (user_id, diary_date),
    KEY idx_diaries_user_date (user_id, diary_date),
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_diaries_mood    CHECK (mood    IN ('기쁨','우울','슬픔','화남','평온','보통','답답','모름')),
    CONSTRAINT chk_diaries_weather CHECK (weather IN ('맑음','흐림','비','눈','바람'))
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- DIARY_TRACK (일기당 MY 1개 / RECOMMENDED 1개)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diary_tracks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    diary_id   BIGINT      NOT NULL,
    track_id   BIGINT      NOT NULL,
    role       VARCHAR(12) NOT NULL,   -- MY | RECOMMENDED
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diary_role (diary_id, role),
    KEY idx_diary_tracks_track (track_id),
    CONSTRAINT fk_dt_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_dt_track FOREIGN KEY (track_id) REFERENCES tracks  (id) ON DELETE CASCADE,
    CONSTRAINT chk_dt_role CHECK (role IN ('MY','RECOMMENDED'))
) ENGINE=InnoDB;
