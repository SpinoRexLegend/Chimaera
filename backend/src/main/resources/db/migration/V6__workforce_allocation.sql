CREATE TABLE workforce_profiles (
 user_id BINARY(16) NOT NULL PRIMARY KEY,
 experience_years INT NOT NULL DEFAULT 0,
 weekly_hours INT NOT NULL DEFAULT 0,
 external_hours INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_workforce_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE workforce_tasks (
 id BINARY(16) NOT NULL PRIMARY KEY,
 quest_id BINARY(16) NOT NULL,
 employee_id BINARY(16) NOT NULL,
 title VARCHAR(200) NOT NULL,
 skills VARCHAR(2000) NOT NULL,
 hours INT NOT NULL,
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_workforce_project FOREIGN KEY (quest_id) REFERENCES quests(id),
 CONSTRAINT fk_workforce_employee FOREIGN KEY (employee_id) REFERENCES users(id),
 INDEX idx_workforce_load (employee_id, completed),
 INDEX idx_workforce_project (quest_id)
) ENGINE=InnoDB;
