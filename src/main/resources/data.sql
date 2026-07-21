-- 개발용 임시 사용자 (인증 구현 전까지 DiaryService의 DEV_USER_ID=1 로 사용)
INSERT INTO users (id, email, nickname, provider, provider_id, created_at, updated_at)
VALUES (1, 'dev@musing.app', '개발자', 'local', 'dev', NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
