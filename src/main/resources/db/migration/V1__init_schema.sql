-- Baseline schema for musing_BE (MySQL 8 / MariaDB 10.6+)

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50)  NOT NULL,
    provider          VARCHAR(20)  NOT NULL,
    provider_id       VARCHAR(255) NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_provider (provider, provider_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token (token_hash),
    KEY idx_refresh_user (user_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tracks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    artist      VARCHAR(255) NOT NULL,
    isrc        VARCHAR(15)  NULL,
    album       VARCHAR(255) NULL,
    artwork_url VARCHAR(500) NULL,
    preview_url VARCHAR(500) NULL,
    valence      DECIMAL(4,3) NULL,
    energy       DECIMAL(4,3) NULL,
    acousticness DECIMAL(4,3) NULL,
    tempo        DECIMAL(6,2) NULL,
    features_source      VARCHAR(20) NULL,
    features_fetched_at  DATETIME    NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tracks_isrc (isrc),
    UNIQUE KEY uq_tracks_name_artist (name, artist)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS diaries (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    diary_date DATE         NOT NULL,
    title      VARCHAR(255) NULL,
    body       TEXT         NULL,
    mood       VARCHAR(10)  NOT NULL,
    weather    VARCHAR(10)  NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diaries_user_date (user_id, diary_date),
    KEY idx_diaries_user_date (user_id, diary_date),
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_diaries_mood CHECK (mood IN ('기쁨','우울','슬픔','화남','평온','보통','답답','모름')),
    CONSTRAINT chk_diaries_weather CHECK (weather IN ('맑음','흐림','비','눈','바람'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS diary_tracks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    diary_id   BIGINT      NOT NULL,
    track_id   BIGINT      NOT NULL,
    role       VARCHAR(12) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diary_role (diary_id, role),
    KEY idx_diary_tracks_track (track_id),
    CONSTRAINT fk_dt_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_dt_track FOREIGN KEY (track_id) REFERENCES tracks  (id) ON DELETE CASCADE,
    CONSTRAINT chk_dt_role CHECK (role IN ('MY','RECOMMENDED'))
) ENGINE=InnoDB;
