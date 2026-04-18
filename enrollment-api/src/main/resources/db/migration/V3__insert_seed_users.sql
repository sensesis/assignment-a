-- 과제 제출용 시드 유저: X-User-Id: 1 (CREATOR), X-User-Id: 2 (CLASSMATE)
INSERT INTO users (user_id, name, role, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, '시드 강사', 'CREATOR', NOW(), NOW()),
       (2, '시드 수강생', 'CLASSMATE', NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

-- IDENTITY 시퀀스를 현재 최대 user_id 이상으로 동기화 (이후 신규 유저 삽입 시 충돌 방지)
SELECT setval(
    pg_get_serial_sequence('users', 'user_id'),
    GREATEST((SELECT COALESCE(MAX(user_id), 0) FROM users), 2)
);
