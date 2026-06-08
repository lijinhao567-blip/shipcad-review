ALTER TABLE parsed_entity ADD COLUMN IF NOT EXISTS cad_handle VARCHAR(255);

ALTER TABLE review_evidence ADD COLUMN IF NOT EXISTS location_json CLOB;
