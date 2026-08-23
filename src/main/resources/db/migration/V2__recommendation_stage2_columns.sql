-- Align schema with Track entity fields introduced for recommendation stage2.
-- Use IF NOT EXISTS so this migration is safe on environments where columns/indexes were
-- already added manually before Flyway versioning was introduced.

ALTER TABLE tracks
    ADD COLUMN IF NOT EXISTS origin VARCHAR(10) NULL COMMENT 'USER/CHART/SURVEY — 유입 경로',
    ADD COLUMN IF NOT EXISTS genre VARCHAR(50) NULL COMMENT 'iTunes primaryGenreName',
    ADD COLUMN IF NOT EXISTS is_korean BOOLEAN NULL COMMENT '아티스트명 한글 포함 → 한국 곡 가산점용';

UPDATE tracks
SET origin = 'USER'
WHERE origin IS NULL;

CREATE INDEX IF NOT EXISTS idx_tracks_features_pending ON tracks (valence, preview_url(100));
CREATE INDEX IF NOT EXISTS idx_tracks_valence_energy ON tracks (valence, energy);
