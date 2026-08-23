-- 추천 2단계: 곡 수집·감정값 매칭용 컬럼 추가
-- 로컬은 ddl-auto=update로 자동 반영되지만, 운영(validate)에서는 이 스크립트를 먼저 적용한다.

ALTER TABLE tracks
    ADD COLUMN origin    VARCHAR(10) NULL COMMENT 'USER/CHART/SURVEY — 유입 경로',
    ADD COLUMN genre     VARCHAR(50) NULL COMMENT 'iTunes primaryGenreName',
    ADD COLUMN is_korean BOOLEAN     NULL COMMENT '아티스트명 한글 포함 → 한국 곡 가산점용';

-- 기존 행은 모두 일기에서 유입된 곡이다.
UPDATE tracks SET origin = 'USER' WHERE origin IS NULL;

-- 감정값 배치 대상 조회( valence IS NULL AND preview_url IS NOT NULL ) 가속
CREATE INDEX idx_tracks_features_pending ON tracks (valence, preview_url(100));
-- 추천 후보 조회(감정값 있는 곡) 가속
CREATE INDEX idx_tracks_valence_energy ON tracks (valence, energy);
