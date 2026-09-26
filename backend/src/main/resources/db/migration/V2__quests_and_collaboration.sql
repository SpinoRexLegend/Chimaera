CREATE TABLE quests (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NULL COMMENT 'Required after authentication rollout',
    title VARCHAR(120) NOT NULL,
    public_summary VARCHAR(600) NOT NULL,
    private_description VARCHAR(8000) NOT NULL,
    category VARCHAR(80) NULL,
    type VARCHAR(40) NOT NULL DEFAULT 'PROJECT',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    collaboration_type VARCHAR(40) NOT NULL DEFAULT 'UNPAID_COLLABORATION',
    expected_duration_days SMALLINT UNSIGNED NULL,
    deadline DATE NOT NULL,
    max_members INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quest_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_quest_capacity CHECK (max_members BETWEEN 2 AND 20),
    INDEX idx_quest_discovery (status, visibility, deadline, deleted_at),
    INDEX idx_quest_owner_status (owner_id, status, updated_at),
    FULLTEXT INDEX ftx_quest_discovery (title, public_summary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quest_requirements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quest_id BINARY(16) NOT NULL,
    skill VARCHAR(255) NOT NULL COMMENT 'Compatibility field used by the current JPA model',
    skill_id BIGINT UNSIGNED NULL,
    importance DECIMAL(4,3) NOT NULL DEFAULT 1.000,
    required_level TINYINT UNSIGNED NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'USER',
    confirmed_by_owner BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_quest_requirement_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE,
    CONSTRAINT fk_quest_requirement_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT,
    CONSTRAINT chk_requirement_importance CHECK (importance BETWEEN 0 AND 1),
    CONSTRAINT chk_requirement_level CHECK (required_level IS NULL OR required_level BETWEEN 1 AND 5),
    CONSTRAINT uq_quest_requirement_text UNIQUE (quest_id, skill),
    INDEX idx_requirement_match (skill_id, confirmed_by_owner, quest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quest_members (
    quest_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    role VARCHAR(40) NOT NULL DEFAULT 'CONTRIBUTOR',
    membership_status VARCHAR(32) NOT NULL DEFAULT 'INVITED',
    joined_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (quest_id, user_id),
    CONSTRAINT fk_quest_member_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quest_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_membership_user_status (user_id, membership_status, updated_at),
    INDEX idx_membership_quest_status (quest_id, membership_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE collaboration_proposals (
    id BINARY(16) NOT NULL,
    quest_id BINARY(16) NOT NULL,
    sender_id BINARY(16) NULL,
    receiver_id BINARY(16) NULL,
    candidate_id VARCHAR(120) NOT NULL COMMENT 'Compatibility field until candidates use user UUIDs',
    message VARCHAR(2000) NOT NULL,
    score_snapshot DECIMAL(6,5) NULL,
    explanation_snapshot JSON NULL,
    disclosure_snapshot JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    expires_at TIMESTAMP(6) NULL,
    sent_at TIMESTAMP(6) NULL,
    responded_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_proposal_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_proposal_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_proposal_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_proposal_score CHECK (score_snapshot IS NULL OR score_snapshot BETWEEN 0 AND 1),
    INDEX idx_proposal_quest_status (quest_id, status, created_at),
    INDEX idx_proposal_receiver_status (receiver_id, status, created_at),
    INDEX idx_proposal_candidate_status (quest_id, candidate_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quest_id BINARY(16) NOT NULL,
    reviewer_id BINARY(16) NOT NULL,
    reviewee_id BINARY(16) NOT NULL,
    contribution_fulfilled TINYINT UNSIGNED NOT NULL,
    communication TINYINT UNSIGNED NOT NULL,
    would_collaborate_again BOOLEAN NOT NULL,
    private_note VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_feedback_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_feedback_not_self CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT chk_feedback_scores CHECK (contribution_fulfilled BETWEEN 1 AND 5 AND communication BETWEEN 1 AND 5),
    CONSTRAINT uq_feedback_pair UNIQUE (quest_id, reviewer_id, reviewee_id),
    INDEX idx_feedback_reviewee (reviewee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE blocks (
    blocker_id BINARY(16) NOT NULL,
    blocked_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_block_not_self CHECK (blocker_id <> blocked_id),
    INDEX idx_blocks_reverse (blocked_id, blocker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BINARY(16) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    detail VARCHAR(2000) NULL,
    moderation_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    assigned_to BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_assignee FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_report_queue (moderation_status, created_at),
    INDEX idx_report_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BINARY(16) NOT NULL,
    type VARCHAR(48) NOT NULL,
    payload JSON NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notification_inbox (user_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
