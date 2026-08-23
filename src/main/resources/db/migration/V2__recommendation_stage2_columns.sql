-- Align schema with Track entity fields introduced for recommendation stage2.
-- Use information_schema checks + dynamic SQL so this migration is safe on environments
-- where columns/indexes were already added manually before Flyway versioning.

SET @add_origin = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tracks'
              AND column_name = 'origin'
        ),
        'SELECT 1',
        'ALTER TABLE tracks ADD COLUMN origin VARCHAR(10) NULL COMMENT ''USER/CHART/SURVEY - 유입 경로'''
    )
);
PREPARE stmt FROM @add_origin;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_genre = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tracks'
              AND column_name = 'genre'
        ),
        'SELECT 1',
        'ALTER TABLE tracks ADD COLUMN genre VARCHAR(50) NULL COMMENT ''iTunes primaryGenreName'''
    )
);
PREPARE stmt FROM @add_genre;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_is_korean = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tracks'
              AND column_name = 'is_korean'
        ),
        'SELECT 1',
        'ALTER TABLE tracks ADD COLUMN is_korean BOOLEAN NULL COMMENT ''아티스트명 한글 포함 -> 한국 곡 가산점용'''
    )
);
PREPARE stmt FROM @add_is_korean;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE tracks
SET origin = 'USER'
WHERE origin IS NULL;

SET @add_idx_tracks_features_pending = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tracks'
              AND index_name = 'idx_tracks_features_pending'
        ),
        'SELECT 1',
        'CREATE INDEX idx_tracks_features_pending ON tracks (valence, preview_url(100))'
    )
);
PREPARE stmt FROM @add_idx_tracks_features_pending;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_tracks_valence_energy = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tracks'
              AND index_name = 'idx_tracks_valence_energy'
        ),
        'SELECT 1',
        'CREATE INDEX idx_tracks_valence_energy ON tracks (valence, energy)'
    )
);
PREPARE stmt FROM @add_idx_tracks_valence_energy;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
