CREATE TABLE shipcad_schema_version (
    version_no INTEGER PRIMARY KEY,
    script_name VARCHAR(255) NOT NULL,
    installed_at DATETIME NOT NULL
);

CREATE TABLE app_user (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(50),
    display_name VARCHAR(80),
    password_hash VARCHAR(100),
    role VARCHAR(32),
    enabled BIT,
    created_at DATETIME,
    updated_at DATETIME,
    password_changed_at DATETIME,
    last_login_at DATETIME
);

CREATE TABLE auth_session (
    id VARCHAR(255) PRIMARY KEY,
    token_hash VARCHAR(64),
    user_id VARCHAR(255),
    created_at DATETIME,
    expires_at DATETIME,
    last_used_at DATETIME,
    revoked_at DATETIME
);

CREATE TABLE project (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    ship_no VARCHAR(255),
    owner VARCHAR(255),
    description VARCHAR(255),
    created_at DATETIME
);

CREATE TABLE project_member (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255),
    user_id VARCHAR(255),
    created_at DATETIME,
    created_by VARCHAR(50)
);

CREATE TABLE drawing (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255),
    drawing_no VARCHAR(255),
    title VARCHAR(255),
    discipline VARCHAR(255),
    created_at DATETIME
);

CREATE TABLE drawing_version (
    id VARCHAR(255) PRIMARY KEY,
    drawing_id VARCHAR(255),
    version_no VARCHAR(255),
    file_name VARCHAR(255),
    file_path VARCHAR(255),
    file_sha256 VARCHAR(255),
    uploaded_by VARCHAR(255),
    uploaded_at DATETIME,
    parse_status VARCHAR(255),
    parse_summary_json CLOB
);

CREATE TABLE parsed_entity (
    id VARCHAR(255) PRIMARY KEY,
    version_id VARCHAR(255),
    entity_type VARCHAR(255),
    layer_name VARCHAR(255),
    text_value VARCHAR(2000),
    block_name VARCHAR(255),
    x DOUBLE,
    y DOUBLE,
    raw_json CLOB
);

CREATE TABLE knowledge_clause (
    id VARCHAR(255) PRIMARY KEY,
    code VARCHAR(255),
    title VARCHAR(255),
    content CLOB,
    source VARCHAR(255),
    tags VARCHAR(2000),
    remediation_hint VARCHAR(4000),
    created_at DATETIME
);

CREATE TABLE review_rule (
    id VARCHAR(255) PRIMARY KEY,
    code VARCHAR(255),
    name VARCHAR(255),
    description VARCHAR(255),
    severity VARCHAR(32),
    enabled BIT NOT NULL,
    knowledge_clause_code VARCHAR(255)
);

CREATE TABLE review_task (
    id VARCHAR(255) PRIMARY KEY,
    version_id VARCHAR(255),
    status VARCHAR(255),
    stage VARCHAR(255),
    started_at DATETIME,
    finished_at DATETIME,
    issue_count INTEGER NOT NULL,
    error_message VARCHAR(255),
    auto_vision BIT,
    auto_ocr BIT,
    force_render BIT,
    vision_confidence DOUBLE,
    ocr_confidence DOUBLE
);

CREATE TABLE review_task_step (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    step_order INTEGER NOT NULL,
    step_code VARCHAR(255),
    step_name VARCHAR(255),
    status VARCHAR(255),
    started_at DATETIME,
    finished_at DATETIME,
    message VARCHAR(4000),
    detail_json VARCHAR(4000)
);

CREATE TABLE review_issue (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    version_id VARCHAR(255),
    rule_code VARCHAR(255),
    title VARCHAR(255),
    description VARCHAR(4000),
    severity VARCHAR(32),
    status VARCHAR(32),
    layer_name VARCHAR(255),
    entity_ref VARCHAR(255),
    suggestion VARCHAR(4000),
    created_at DATETIME,
    updated_at DATETIME,
    assignee VARCHAR(255)
);

CREATE TABLE review_evidence (
    id VARCHAR(255) PRIMARY KEY,
    issue_id VARCHAR(255),
    task_id VARCHAR(255),
    version_id VARCHAR(255),
    rule_code VARCHAR(255),
    evidence_type VARCHAR(32),
    source_id VARCHAR(255),
    source_label VARCHAR(255),
    summary VARCHAR(4000),
    payload_json CLOB,
    confidence DOUBLE,
    created_at DATETIME
);

CREATE TABLE report_document (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    version_id VARCHAR(255),
    content CLOB,
    created_at DATETIME
);

CREATE TABLE remediation_record (
    id VARCHAR(255) PRIMARY KEY,
    issue_id VARCHAR(255),
    task_id VARCHAR(255),
    version_id VARCHAR(255),
    operator VARCHAR(255),
    action VARCHAR(255),
    from_status VARCHAR(255),
    to_status VARCHAR(255),
    assignee VARCHAR(255),
    report_id VARCHAR(255),
    note VARCHAR(4000),
    created_at DATETIME
);

CREATE TABLE audit_log (
    id VARCHAR(255) PRIMARY KEY,
    actor VARCHAR(255),
    action VARCHAR(255),
    target_type VARCHAR(255),
    target_id VARCHAR(255),
    detail_json CLOB,
    created_at DATETIME
);

INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)
VALUES (1, 'V1__initial_schema.sql', CURRENT_TIMESTAMP);
