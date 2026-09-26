CREATE TABLE agent_runs (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    quest_id BINARY(16) NULL,
    goal VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    model_name VARCHAR(100) NULL,
    correlation_id VARCHAR(100) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at TIMESTAMP(6) NULL,
    error_code VARCHAR(80) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_run_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_agent_run_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE SET NULL,
    CONSTRAINT uq_agent_run_correlation UNIQUE (correlation_id),
    INDEX idx_agent_run_quest (quest_id, started_at),
    INDEX idx_agent_run_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_actions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_id BINARY(16) NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    input_summary JSON NULL,
    result_summary JSON NULL,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by BINARY(16) NULL,
    approved_at TIMESTAMP(6) NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms INT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_action_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_action_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_agent_action_sequence UNIQUE (run_id, sequence_no),
    INDEX idx_agent_action_tool (tool_name, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BINARY(16) NULL,
    quest_id BINARY(16) NULL,
    candidate_id BINARY(16) NULL,
    rank_position SMALLINT UNSIGNED NULL,
    score DECIMAL(6,5) NULL,
    model_version VARCHAR(80) NOT NULL,
    features JSON NULL,
    explanation JSON NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_recommendation_quest FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE SET NULL,
    CONSTRAINT fk_recommendation_candidate FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_recommendation_score CHECK (score IS NULL OR score BETWEEN 0 AND 1),
    INDEX idx_recommendation_user_time (user_id, occurred_at),
    INDEX idx_recommendation_quest_time (quest_id, occurred_at),
    INDEX idx_recommendation_training (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_dispatch (status, available_at, created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_keys (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    principal_key VARCHAR(180) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status SMALLINT UNSIGNED NULL,
    response_body JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_scope UNIQUE (principal_key, operation, idempotency_key),
    INDEX idx_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
