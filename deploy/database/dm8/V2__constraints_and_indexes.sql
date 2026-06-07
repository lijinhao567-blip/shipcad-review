ALTER TABLE app_user MODIFY username VARCHAR(50) NOT NULL;
ALTER TABLE app_user MODIFY display_name VARCHAR(80) NOT NULL;
ALTER TABLE app_user MODIFY password_hash VARCHAR(100) NOT NULL;
ALTER TABLE app_user MODIFY role VARCHAR(32) NOT NULL;
ALTER TABLE auth_session MODIFY token_hash VARCHAR(64) NOT NULL;
ALTER TABLE auth_session MODIFY user_id VARCHAR(255) NOT NULL;
ALTER TABLE auth_session MODIFY created_at DATETIME NOT NULL;
ALTER TABLE auth_session MODIFY expires_at DATETIME NOT NULL;
ALTER TABLE project_member MODIFY project_id VARCHAR(255) NOT NULL;
ALTER TABLE project_member MODIFY user_id VARCHAR(255) NOT NULL;
ALTER TABLE project_member MODIFY created_at DATETIME NOT NULL;
ALTER TABLE project_member MODIFY created_by VARCHAR(50) NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_username UNIQUE (username);
ALTER TABLE auth_session
    ADD CONSTRAINT uk_auth_session_token_hash UNIQUE (token_hash);
ALTER TABLE project_member
    ADD CONSTRAINT uk_project_member_project_user UNIQUE (project_id, user_id);
ALTER TABLE knowledge_clause
    ADD CONSTRAINT uk_knowledge_clause_code UNIQUE (code);
ALTER TABLE review_rule
    ADD CONSTRAINT uk_review_rule_code UNIQUE (code);

CREATE INDEX idx_auth_session_user_id ON auth_session(user_id);
CREATE INDEX idx_auth_session_expires_at ON auth_session(expires_at);
CREATE INDEX idx_project_member_project_id ON project_member(project_id);
CREATE INDEX idx_project_member_user_id ON project_member(user_id);
CREATE INDEX idx_drawing_project_id ON drawing(project_id);
CREATE INDEX idx_drawing_version_drawing_id ON drawing_version(drawing_id);
CREATE INDEX idx_parsed_entity_version_id ON parsed_entity(version_id);
CREATE INDEX idx_parsed_entity_version_type ON parsed_entity(version_id, entity_type);
CREATE INDEX idx_parsed_entity_version_layer ON parsed_entity(version_id, layer_name);
CREATE INDEX idx_review_task_version_id ON review_task(version_id);
CREATE INDEX idx_review_task_status ON review_task(status);
CREATE INDEX idx_review_task_step_task_order ON review_task_step(task_id, step_order);
CREATE INDEX idx_review_issue_task_id ON review_issue(task_id);
CREATE INDEX idx_review_issue_version_id ON review_issue(version_id);
CREATE INDEX idx_review_issue_status ON review_issue(status);
CREATE INDEX idx_review_evidence_issue_id ON review_evidence(issue_id);
CREATE INDEX idx_review_evidence_task_id ON review_evidence(task_id);
CREATE INDEX idx_review_evidence_version_type ON review_evidence(version_id, evidence_type);
CREATE INDEX idx_remediation_issue_created ON remediation_record(issue_id, created_at);
CREATE INDEX idx_remediation_task_id ON remediation_record(task_id);
CREATE INDEX idx_report_task_id ON report_document(task_id);
CREATE INDEX idx_report_version_id ON report_document(version_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id);

ALTER TABLE auth_session
    ADD CONSTRAINT fk_auth_session_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE project_member
    ADD CONSTRAINT fk_project_member_project
    FOREIGN KEY (project_id) REFERENCES project(id);
ALTER TABLE project_member
    ADD CONSTRAINT fk_project_member_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE drawing
    ADD CONSTRAINT fk_drawing_project
    FOREIGN KEY (project_id) REFERENCES project(id);
ALTER TABLE drawing_version
    ADD CONSTRAINT fk_drawing_version_drawing
    FOREIGN KEY (drawing_id) REFERENCES drawing(id);
ALTER TABLE parsed_entity
    ADD CONSTRAINT fk_parsed_entity_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_rule
    ADD CONSTRAINT fk_review_rule_clause
    FOREIGN KEY (knowledge_clause_code) REFERENCES knowledge_clause(code);
ALTER TABLE review_task
    ADD CONSTRAINT fk_review_task_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_task_step
    ADD CONSTRAINT fk_review_task_step_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_issue
    ADD CONSTRAINT fk_review_issue_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_issue
    ADD CONSTRAINT fk_review_issue_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT fk_review_evidence_issue
    FOREIGN KEY (issue_id) REFERENCES review_issue(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT fk_review_evidence_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT fk_review_evidence_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE report_document
    ADD CONSTRAINT fk_report_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE report_document
    ADD CONSTRAINT fk_report_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT fk_remediation_issue
    FOREIGN KEY (issue_id) REFERENCES review_issue(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT fk_remediation_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT fk_remediation_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);

INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)
VALUES (2, 'V2__constraints_and_indexes.sql', CURRENT_TIMESTAMP);
