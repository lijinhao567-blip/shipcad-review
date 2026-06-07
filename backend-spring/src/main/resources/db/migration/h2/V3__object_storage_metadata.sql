ALTER TABLE drawing_version
    ADD COLUMN IF NOT EXISTS storage_mode VARCHAR(32);

ALTER TABLE drawing_version
    ADD COLUMN IF NOT EXISTS file_object_key VARCHAR(1024);

UPDATE drawing_version
SET storage_mode = 'local'
WHERE storage_mode IS NULL;

CREATE INDEX IF NOT EXISTS idx_drawing_version_object_key ON drawing_version(file_object_key);
