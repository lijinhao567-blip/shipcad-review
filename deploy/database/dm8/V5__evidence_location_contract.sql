ALTER TABLE parsed_entity ADD cad_handle VARCHAR(255);

ALTER TABLE review_evidence ADD location_json CLOB;

INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)
VALUES (5, 'V5__evidence_location_contract.sql', CURRENT_TIMESTAMP);
