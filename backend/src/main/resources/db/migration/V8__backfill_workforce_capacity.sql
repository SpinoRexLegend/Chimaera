ALTER TABLE workforce_profiles ALTER COLUMN weekly_hours SET DEFAULT 40;

INSERT INTO workforce_profiles (user_id, experience_years, weekly_hours, external_hours)
SELECT u.id, 0, 40, 0
FROM users u
LEFT JOIN workforce_profiles w ON w.user_id = u.id
WHERE w.user_id IS NULL;
