ALTER TABLE report_document
    ADD COLUMN IF NOT EXISTS storage_mode VARCHAR(32);

ALTER TABLE report_document
    ADD COLUMN IF NOT EXISTS content_object_key VARCHAR(1024);

ALTER TABLE report_document
    ADD COLUMN IF NOT EXISTS content_path VARCHAR(1024);

ALTER TABLE report_document
    ADD COLUMN IF NOT EXISTS content_size_bytes BIGINT;

UPDATE report_document
SET storage_mode = 'database'
WHERE storage_mode IS NULL;

CREATE INDEX IF NOT EXISTS idx_report_content_object_key ON report_document(content_object_key);
