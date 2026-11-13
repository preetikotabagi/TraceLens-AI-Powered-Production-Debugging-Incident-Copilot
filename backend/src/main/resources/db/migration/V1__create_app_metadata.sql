-- V1: foundation schema.
-- A tiny key/value table that proves migrations run and gives later milestones a place for
-- application-level metadata. Log, incident and analysis tables arrive in later migrations.
CREATE TABLE app_metadata (
    key        VARCHAR(100) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO app_metadata (key, value) VALUES ('schema_initialized_by', 'tracelens-foundation');
