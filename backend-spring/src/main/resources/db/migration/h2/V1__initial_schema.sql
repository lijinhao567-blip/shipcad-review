CREATE TABLE IF NOT EXISTS app_user (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(50),
    display_name VARCHAR(80),
    password_hash VARCHAR(100),
    role VARCHAR(32),
    enabled BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS auth_session (
    id VARCHAR(255) PRIMARY KEY,
    token_hash VARCHAR(64),
    user_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS project (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    ship_no VARCHAR(255),
    owner VARCHAR(255),
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS project_member (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255),
    user_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS drawing (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255),
    drawing_no VARCHAR(255),
    title VARCHAR(255),
    discipline VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS drawing_version (
    id VARCHAR(255) PRIMARY KEY,
    drawing_id VARCHAR(255),
    version_no VARCHAR(255),
    file_name VARCHAR(255),
    file_path VARCHAR(255),
    file_sha256 VARCHAR(255),
    uploaded_by VARCHAR(255),
    uploaded_at TIMESTAMP WITH TIME ZONE,
    parse_status VARCHAR(255),
    parse_summary_json CLOB
);

CREATE TABLE IF NOT EXISTS parsed_entity (
    id VARCHAR(255) PRIMARY KEY,
    version_id VARCHAR(255),
    entity_type VARCHAR(255),
    layer_name VARCHAR(255),
    text_value VARCHAR(2000),
    block_name VARCHAR(255),
    x DOUBLE PRECISION,
    y DOUBLE PRECISION,
    raw_json CLOB
);

CREATE TABLE IF NOT EXISTS knowledge_clause (
    id VARCHAR(255) PRIMARY KEY,
    code VARCHAR(255),
    title VARCHAR(255),
    content CLOB,
    source VARCHAR(255),
    tags VARCHAR(2000),
    remediation_hint VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS review_rule (
    id VARCHAR(255) PRIMARY KEY,
    code VARCHAR(255),
    name VARCHAR(255),
    description VARCHAR(255),
    severity VARCHAR(32),
    enabled BOOLEAN NOT NULL,
    knowledge_clause_code VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS review_task (
    id VARCHAR(255) PRIMARY KEY,
    version_id VARCHAR(255),
    status VARCHAR(255),
    stage VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    issue_count INTEGER NOT NULL,
    error_message VARCHAR(255),
    auto_vision BOOLEAN,
    auto_ocr BOOLEAN,
    force_render BOOLEAN,
    vision_confidence DOUBLE PRECISION,
    ocr_confidence DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS review_task_step (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    step_order INTEGER NOT NULL,
    step_code VARCHAR(255),
    step_name VARCHAR(255),
    status VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    message VARCHAR(4000),
    detail_json VARCHAR(4000)
);

CREATE TABLE IF NOT EXISTS review_issue (
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
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    assignee VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS review_evidence (
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
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS report_document (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    version_id VARCHAR(255),
    content CLOB,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS remediation_record (
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
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(255) PRIMARY KEY,
    actor VARCHAR(255),
    action VARCHAR(255),
    target_type VARCHAR(255),
    target_id VARCHAR(255),
    detail_json CLOB,
    created_at TIMESTAMP WITH TIME ZONE
);
