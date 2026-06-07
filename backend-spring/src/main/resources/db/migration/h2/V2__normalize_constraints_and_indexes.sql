ALTER TABLE app_user ALTER COLUMN role VARCHAR(32);
ALTER TABLE review_rule ALTER COLUMN severity VARCHAR(32);
ALTER TABLE review_issue ALTER COLUMN severity VARCHAR(32);
ALTER TABLE review_issue ALTER COLUMN status VARCHAR(32);
ALTER TABLE review_evidence ALTER COLUMN evidence_type VARCHAR(32);

ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN role SET NOT NULL;
ALTER TABLE auth_session ALTER COLUMN token_hash SET NOT NULL;
ALTER TABLE auth_session ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE auth_session ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE auth_session ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE project_member ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE project_member ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE project_member ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE project_member ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT IF NOT EXISTS uk_app_user_username UNIQUE (username);
ALTER TABLE auth_session
    ADD CONSTRAINT IF NOT EXISTS uk_auth_session_token_hash UNIQUE (token_hash);
ALTER TABLE project_member
    ADD CONSTRAINT IF NOT EXISTS uk_project_member_project_user UNIQUE (project_id, user_id);
ALTER TABLE knowledge_clause
    ADD CONSTRAINT IF NOT EXISTS uk_knowledge_clause_code UNIQUE (code);
ALTER TABLE review_rule
    ADD CONSTRAINT IF NOT EXISTS uk_review_rule_code UNIQUE (code);

CREATE INDEX IF NOT EXISTS idx_auth_session_user_id ON auth_session(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires_at ON auth_session(expires_at);
CREATE INDEX IF NOT EXISTS idx_project_member_project_id ON project_member(project_id);
CREATE INDEX IF NOT EXISTS idx_project_member_user_id ON project_member(user_id);
CREATE INDEX IF NOT EXISTS idx_drawing_project_id ON drawing(project_id);
CREATE INDEX IF NOT EXISTS idx_drawing_version_drawing_id ON drawing_version(drawing_id);
CREATE INDEX IF NOT EXISTS idx_parsed_entity_version_id ON parsed_entity(version_id);
CREATE INDEX IF NOT EXISTS idx_parsed_entity_version_type ON parsed_entity(version_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_parsed_entity_version_layer ON parsed_entity(version_id, layer_name);
CREATE INDEX IF NOT EXISTS idx_review_task_version_id ON review_task(version_id);
CREATE INDEX IF NOT EXISTS idx_review_task_status ON review_task(status);
CREATE INDEX IF NOT EXISTS idx_review_task_step_task_order ON review_task_step(task_id, step_order);
CREATE INDEX IF NOT EXISTS idx_review_issue_task_id ON review_issue(task_id);
CREATE INDEX IF NOT EXISTS idx_review_issue_version_id ON review_issue(version_id);
CREATE INDEX IF NOT EXISTS idx_review_issue_status ON review_issue(status);
CREATE INDEX IF NOT EXISTS idx_review_evidence_issue_id ON review_evidence(issue_id);
CREATE INDEX IF NOT EXISTS idx_review_evidence_task_id ON review_evidence(task_id);
CREATE INDEX IF NOT EXISTS idx_review_evidence_version_type ON review_evidence(version_id, evidence_type);
CREATE INDEX IF NOT EXISTS idx_remediation_issue_created ON remediation_record(issue_id, created_at);
CREATE INDEX IF NOT EXISTS idx_remediation_task_id ON remediation_record(task_id);
CREATE INDEX IF NOT EXISTS idx_report_task_id ON report_document(task_id);
CREATE INDEX IF NOT EXISTS idx_report_version_id ON report_document(version_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_log(target_type, target_id);

ALTER TABLE auth_session
    ADD CONSTRAINT IF NOT EXISTS fk_auth_session_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE project_member
    ADD CONSTRAINT IF NOT EXISTS fk_project_member_project
    FOREIGN KEY (project_id) REFERENCES project(id);
ALTER TABLE project_member
    ADD CONSTRAINT IF NOT EXISTS fk_project_member_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE drawing
    ADD CONSTRAINT IF NOT EXISTS fk_drawing_project
    FOREIGN KEY (project_id) REFERENCES project(id);
ALTER TABLE drawing_version
    ADD CONSTRAINT IF NOT EXISTS fk_drawing_version_drawing
    FOREIGN KEY (drawing_id) REFERENCES drawing(id);
ALTER TABLE parsed_entity
    ADD CONSTRAINT IF NOT EXISTS fk_parsed_entity_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_rule
    ADD CONSTRAINT IF NOT EXISTS fk_review_rule_clause
    FOREIGN KEY (knowledge_clause_code) REFERENCES knowledge_clause(code);
ALTER TABLE review_task
    ADD CONSTRAINT IF NOT EXISTS fk_review_task_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_task_step
    ADD CONSTRAINT IF NOT EXISTS fk_review_task_step_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_issue
    ADD CONSTRAINT IF NOT EXISTS fk_review_issue_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_issue
    ADD CONSTRAINT IF NOT EXISTS fk_review_issue_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT IF NOT EXISTS fk_review_evidence_issue
    FOREIGN KEY (issue_id) REFERENCES review_issue(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT IF NOT EXISTS fk_review_evidence_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE review_evidence
    ADD CONSTRAINT IF NOT EXISTS fk_review_evidence_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE report_document
    ADD CONSTRAINT IF NOT EXISTS fk_report_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE report_document
    ADD CONSTRAINT IF NOT EXISTS fk_report_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT IF NOT EXISTS fk_remediation_issue
    FOREIGN KEY (issue_id) REFERENCES review_issue(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT IF NOT EXISTS fk_remediation_task
    FOREIGN KEY (task_id) REFERENCES review_task(id);
ALTER TABLE remediation_record
    ADD CONSTRAINT IF NOT EXISTS fk_remediation_version
    FOREIGN KEY (version_id) REFERENCES drawing_version(id);
