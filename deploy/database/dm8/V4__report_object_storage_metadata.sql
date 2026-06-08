ALTER TABLE report_document ADD storage_mode VARCHAR(32);
ALTER TABLE report_document ADD content_object_key VARCHAR(1024);
ALTER TABLE report_document ADD content_path VARCHAR(1024);
ALTER TABLE report_document ADD content_size_bytes BIGINT;

UPDATE report_document
SET storage_mode = 'database'
WHERE storage_mode IS NULL;

CREATE INDEX idx_report_content_object_key ON report_document(content_object_key);

INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)
VALUES (4, 'V4__report_object_storage_metadata.sql', CURRENT_TIMESTAMP);
