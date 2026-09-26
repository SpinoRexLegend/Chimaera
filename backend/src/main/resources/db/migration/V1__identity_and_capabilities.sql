CREATE TABLE users (
    id BINARY(16) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    bio VARCHAR(1000) NULL,
    institution VARCHAR(180) NULL,
    availability_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    profile_visibility VARCHAR(32) NOT NULL DEFAULT 'MEMBERS',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_users_discovery (status, availability_status, deleted_at),
    INDEX idx_users_institution (institution)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_identities (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    email_normalized VARCHAR(254) NULL,
    email_verified_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_auth_provider_subject UNIQUE (provider, subject),
    CONSTRAINT uq_auth_provider_email UNIQUE (provider, email_normalized),
    INDEX idx_auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skills (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    normalized_name VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    category VARCHAR(80) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_skill_normalized UNIQUE (normalized_name),
    INDEX idx_skills_category_name (category, normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_aliases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    skill_id BIGINT UNSIGNED NOT NULL,
    alias_normalized VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_skill_alias_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE,
    CONSTRAINT uq_skill_alias UNIQUE (alias_normalized),
    INDEX idx_skill_alias_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_skills (
    user_id BINARY(16) NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    proficiency TINYINT UNSIGNED NULL,
    evidence_type VARCHAR(40) NOT NULL DEFAULT 'SELF_DECLARED',
    evidence_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_used_at DATE NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, skill_id),
    CONSTRAINT fk_user_skill_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_skill_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_skill_proficiency CHECK (proficiency IS NULL OR proficiency BETWEEN 1 AND 5),
    INDEX idx_user_skills_matching (skill_id, proficiency, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_interests (
    user_id BINARY(16) NOT NULL,
    interest_tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, interest_tag),
    CONSTRAINT fk_user_interest_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_interest_discovery (interest_tag, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE availability_windows (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BINARY(16) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_availability_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_availability_range CHECK (ends_at > starts_at),
    INDEX idx_availability_lookup (user_id, starts_at, ends_at),
    INDEX idx_availability_time (starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
