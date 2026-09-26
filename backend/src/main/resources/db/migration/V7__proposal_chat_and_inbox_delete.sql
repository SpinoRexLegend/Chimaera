ALTER TABLE collaboration_proposals
    ADD COLUMN receiver_deleted_at TIMESTAMP(6) NULL AFTER responded_at,
    ADD INDEX idx_proposal_receiver_visible (receiver_id, receiver_deleted_at, created_at);

CREATE TABLE proposal_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id BINARY(16) NOT NULL,
    sender_id BINARY(16) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_message_proposal FOREIGN KEY (proposal_id) REFERENCES collaboration_proposals(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_message_conversation (proposal_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
