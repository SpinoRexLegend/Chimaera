CREATE TABLE user_resumes (
    user_id BINARY(16) PRIMARY KEY,
    content MEDIUMBLOB NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
