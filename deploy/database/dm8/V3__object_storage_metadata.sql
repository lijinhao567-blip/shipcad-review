ALTER TABLE drawing_version ADD storage_mode VARCHAR(32);
ALTER TABLE drawing_version ADD file_object_key VARCHAR(1024);

UPDATE drawing_version
SET storage_mode = 'local'
WHERE storage_mode IS NULL;

CREATE INDEX idx_drawing_version_object_key ON drawing_version(file_object_key);

INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)
VALUES (3, 'V3__object_storage_metadata.sql', CURRENT_TIMESTAMP);
