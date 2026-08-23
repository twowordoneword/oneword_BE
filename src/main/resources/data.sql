-- 개발용 시드 사용자 (로컬 프로파일에서만 삽입)
INSERT INTO users (id, email, nickname, provider, provider_id, created_at, updated_at)
VALUES (1, 'dev@musing.app', '개발자', 'local', 'dev', NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
