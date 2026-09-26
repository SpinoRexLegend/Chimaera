-- Widen the existing proficiency column to match the JPA Integer mapping.
-- The existing 1..5 check constraint continues to enforce valid values.
ALTER TABLE user_skills MODIFY proficiency INT UNSIGNED NULL;
