-- 운영 DB 스키마 드리프트 점검 (#23)
--
-- Flyway baseline으로 넘어온 DB는 V1이 실행된 적이 없어 제약이 빠져 있을 수 있다.
-- 마이그레이션(V1~V3)이 정의한 인덱스·외래키가 실제로 있는지 대조한다.
--
--   mysql -h <rds-endpoint> -u musing -p musing < deploy/schema-check.sql
--
-- 결과의 status가 전부 OK면 통과. MISSING/MISMATCH/UNEXPECTED가 하나라도 있으면 조치가 필요하다.
-- 이름 비교는 BINARY로 한다 — information_schema와 리터럴의 콜레이션이 달라 생기는 오류를 피하려고.

-- 1) 인덱스: 이름·컬럼 순서·유니크 여부까지 확인
WITH expected AS (
              SELECT 'users'          AS tbl, 'PRIMARY'                     AS idx, 'id'                   AS cols, 1 AS uniq
    UNION ALL SELECT 'users',          'uq_users_provider',           'provider,provider_id', 1
    UNION ALL SELECT 'users',          'idx_users_email',             'email',                0
    UNION ALL SELECT 'refresh_tokens', 'PRIMARY',                     'id',                   1
    UNION ALL SELECT 'refresh_tokens', 'uq_refresh_token',            'token_hash',           1
    UNION ALL SELECT 'refresh_tokens', 'idx_refresh_user',            'user_id',              0
    UNION ALL SELECT 'tracks',         'PRIMARY',                     'id',                   1
    UNION ALL SELECT 'tracks',         'uq_tracks_isrc',              'isrc',                 1
    UNION ALL SELECT 'tracks',         'uq_tracks_name_artist',       'name,artist',          1
    UNION ALL SELECT 'tracks',         'idx_tracks_features_pending', 'valence,preview_url',  0
    UNION ALL SELECT 'tracks',         'idx_tracks_valence_energy',   'valence,energy',       0
    UNION ALL SELECT 'diaries',        'PRIMARY',                     'id',                   1
    UNION ALL SELECT 'diaries',        'uq_diaries_user_date',        'user_id,diary_date',   1
    -- V1의 idx_diaries_user_date는 위 유니크와 컬럼이 같아 중복이다. 있든 없든 문제가 없어 점검하지 않는다.
    UNION ALL SELECT 'diary_tracks',   'PRIMARY',                     'id',                   1
    UNION ALL SELECT 'diary_tracks',   'uq_diary_role',               'diary_id,role',        1
    UNION ALL SELECT 'diary_tracks',   'idx_diary_tracks_track',      'track_id',             0
),
actual AS (
    SELECT table_name AS tbl,
           index_name AS idx,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS cols,
           1 - MAX(non_unique) AS uniq
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
    GROUP BY table_name, index_name
)
SELECT * FROM (
SELECT 'index' AS kind,
       e.tbl,
       e.idx AS name,
       CASE
           WHEN a.idx IS NULL THEN 'MISSING'
           WHEN BINARY a.cols <> BINARY e.cols OR a.uniq <> e.uniq THEN 'MISMATCH'
           ELSE 'OK'
       END AS status,
       CONCAT('expected ', e.cols, IF(e.uniq = 1, ' UNIQUE', ''),
              ' / actual ', COALESCE(CONCAT(a.cols, IF(a.uniq = 1, ' UNIQUE', '')), '-')) AS detail
FROM expected e
LEFT JOIN actual a ON BINARY a.tbl = BINARY e.tbl AND BINARY a.idx = BINARY e.idx

UNION ALL

-- 2) users.email 단일 컬럼 유니크는 V3에서 제거돼야 한다.
--    (제공자가 달라도 같은 이메일로 가입할 수 있어야 함. Hibernate 자동생성 이름일 수도 있어 이름이 아니라 정의로 찾는다.)
SELECT 'index', a.tbl, a.idx, 'UNEXPECTED', 'users.email 단일 유니크가 남아 있음 — V3 미적용 의심'
FROM actual a
WHERE BINARY a.tbl = BINARY 'users' AND BINARY a.cols = BINARY 'email' AND a.uniq = 1

UNION ALL

-- 3) 외래키: 참조 대상과 ON DELETE CASCADE까지 확인
SELECT 'foreign_key',
       e.tbl,
       e.fk,
       CASE
           WHEN r.constraint_name IS NULL THEN 'MISSING'
           WHEN BINARY r.referenced_table_name <> BINARY e.ref OR BINARY r.delete_rule <> BINARY 'CASCADE' THEN 'MISMATCH'
           ELSE 'OK'
       END,
       CONCAT('expected -> ', e.ref, ' ON DELETE CASCADE / actual -> ',
              COALESCE(CONCAT(r.referenced_table_name, ' ON DELETE ', r.delete_rule), '-'))
FROM (
              SELECT 'refresh_tokens' AS tbl, 'fk_refresh_user' AS fk, 'users' AS ref
    UNION ALL SELECT 'diaries',      'fk_diaries_user', 'users'
    UNION ALL SELECT 'diary_tracks', 'fk_dt_diary',     'diaries'
    UNION ALL SELECT 'diary_tracks', 'fk_dt_track',     'tracks'
) e
LEFT JOIN information_schema.referential_constraints r
       ON r.constraint_schema = DATABASE()
      AND BINARY r.table_name = BINARY e.tbl
      AND BINARY r.constraint_name = BINARY e.fk
) checks
-- 문제가 있는 행을 먼저 보여준다
ORDER BY BINARY status = BINARY 'OK', tbl, name;

-- 4) Flyway 적용 이력 — V3까지 success=1 이어야 한다.
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
