-- 소셜 계정마다 별도 사용자로 취급한다.
-- 같은 사람이 카카오와 구글로 각각 가입하면 이메일이 겹칠 수 있는데, users.email에 유니크가
-- 걸려 있으면 두 번째 가입이 제약 위반으로 영구히 막힌다.
-- 계정 식별은 uq_users_provider(provider, provider_id)가 담당하므로 email 유니크는 없앤다.
--
-- 인덱스를 '이름'이 아니라 '정의'로 찾는 이유: 초기에 ddl-auto로 만들어진 DB에는 같은 제약이
-- Hibernate 자동생성 이름(UK6dotkott2kjsp8vw4d0m25fb7 같은)으로 들어 있어, V1의 uq_users_email만
-- 찾으면 그런 환경에서는 아무것도 지우지 못하고 조용히 넘어간다.

-- 1) users(email) 단일 컬럼 유니크 인덱스가 있으면 이름과 무관하게 제거한다.
SET @email_unique_index = (
    SELECT s.index_name
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'users'
      AND s.column_name = 'email'
      AND s.non_unique = 0
      AND s.index_name <> 'PRIMARY'
      -- 복합 유니크(예: (provider, email))는 건드리지 않는다
      AND (SELECT COUNT(*)
           FROM information_schema.statistics t
           WHERE t.table_schema = DATABASE()
             AND t.table_name = 'users'
             AND t.index_name = s.index_name) = 1
    LIMIT 1
);
SET @drop_email_unique = IF(
    @email_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE users DROP INDEX `', @email_unique_index, '`')
);
PREPARE stmt FROM @drop_email_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 조회용 비유니크 인덱스는 남긴다.
SET @add_idx_users_email = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'users'
              AND index_name = 'idx_users_email'
        ),
        'SELECT 1',
        'CREATE INDEX idx_users_email ON users (email)'
    )
);
PREPARE stmt FROM @add_idx_users_email;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 계정 식별 제약을 보장한다.
-- 이게 없으면 최초 로그인이 동시에 두 번 들어올 때 사용자 행이 중복 생성된다.
-- (baseline으로 넘어온 DB에는 V1이 실행된 적이 없어 이 제약이 빠져 있을 수 있다.)
SET @add_uq_users_provider = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'users'
              AND index_name = 'uq_users_provider'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD UNIQUE KEY uq_users_provider (provider, provider_id)'
    )
);
PREPARE stmt FROM @add_uq_users_provider;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
