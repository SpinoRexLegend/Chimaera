INSERT INTO collaboration_proposals
    (id, quest_id, sender_id, receiver_id, candidate_id, message, status, sent_at, created_at)
SELECT UUID_TO_BIN(UUID()), t.quest_id, q.owner_id, t.employee_id, BIN_TO_UUID(t.employee_id),
       CONCAT('Workforce allocation awaiting acceptance: ', q.title),
       'SENT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM workforce_tasks t
JOIN quests q ON q.id = t.quest_id
WHERE t.proposal_id IS NULL AND t.completed = FALSE
GROUP BY t.quest_id, q.owner_id, t.employee_id, q.title;

UPDATE workforce_tasks t
JOIN collaboration_proposals p
  ON p.quest_id = t.quest_id
 AND p.receiver_id = t.employee_id
 AND p.message LIKE 'Workforce allocation awaiting acceptance:%'
SET t.proposal_id = p.id,
    t.assignment_status = 'PENDING'
WHERE t.proposal_id IS NULL AND t.completed = FALSE;
