ALTER TABLE workforce_tasks
    ADD COLUMN proposal_id BINARY(16) NULL AFTER employee_id,
    ADD COLUMN assignment_status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED' AFTER hours,
    ADD CONSTRAINT fk_workforce_task_proposal FOREIGN KEY (proposal_id) REFERENCES collaboration_proposals(id) ON DELETE RESTRICT,
    ADD INDEX idx_workforce_task_proposal (proposal_id),
    ADD INDEX idx_workforce_task_inbox (employee_id, assignment_status, completed);

-- Existing assignments predate the consent workflow and remain valid.
UPDATE workforce_tasks SET assignment_status = 'ACCEPTED' WHERE assignment_status IS NULL;
