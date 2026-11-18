-- V2: Logging, Error Grouping, Incident Detection, and AI Investigation schema

CREATE TABLE error_groups (
    fingerprint         VARCHAR(64) PRIMARY KEY,
    error_type          VARCHAR(255) NOT NULL,
    service_name        VARCHAR(100) NOT NULL,
    message_template    TEXT         NOT NULL,
    sample_stack_trace  TEXT,
    occurrence_count    INT          NOT NULL DEFAULT 1,
    first_seen          TIMESTAMPTZ  NOT NULL,
    last_seen           TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_error_groups_service ON error_groups(service_name);
CREATE INDEX idx_error_groups_last_seen ON error_groups(last_seen DESC);

CREATE TABLE incidents (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    service_name        VARCHAR(100) NOT NULL,
    severity            VARCHAR(20)  NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN, INVESTIGATING, RESOLVED, MUTED
    fingerprint         VARCHAR(64)  REFERENCES error_groups(fingerprint) ON DELETE SET NULL,
    trigger_rule        VARCHAR(100) NOT NULL,
    occurrence_count    INT          NOT NULL DEFAULT 1,
    first_seen          TIMESTAMPTZ  NOT NULL,
    last_seen           TIMESTAMPTZ  NOT NULL,
    summary             TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incidents_service ON incidents(service_name);
CREATE INDEX idx_incidents_last_seen ON incidents(last_seen DESC);
CREATE INDEX idx_incidents_fingerprint ON incidents(fingerprint);

CREATE TABLE logs (
    id                  BIGSERIAL PRIMARY KEY,
    timestamp           TIMESTAMPTZ  NOT NULL,
    level               VARCHAR(10)  NOT NULL, -- TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    service_name        VARCHAR(100) NOT NULL,
    environment         VARCHAR(50)  NOT NULL DEFAULT 'production',
    message             TEXT         NOT NULL,
    logger              VARCHAR(255),
    thread_name         VARCHAR(100),
    request_id          VARCHAR(64),
    user_id             VARCHAR(100),
    trace_id            VARCHAR(64),
    span_id             VARCHAR(64),
    error_type          VARCHAR(255),
    stack_trace         TEXT,
    fingerprint         VARCHAR(64)  REFERENCES error_groups(fingerprint) ON DELETE SET NULL,
    incident_id         BIGINT       REFERENCES incidents(id) ON DELETE SET NULL,
    metadata            TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_logs_timestamp ON logs(timestamp DESC);
CREATE INDEX idx_logs_level ON logs(level);
CREATE INDEX idx_logs_service ON logs(service_name);
CREATE INDEX idx_logs_fingerprint ON logs(fingerprint);
CREATE INDEX idx_logs_incident ON logs(incident_id);
CREATE INDEX idx_logs_request_id ON logs(request_id);

CREATE TABLE incident_investigations (
    id                  BIGSERIAL PRIMARY KEY,
    incident_id         BIGINT       NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    model               VARCHAR(100) NOT NULL,
    summary             TEXT         NOT NULL,
    suspicious_sequence TEXT         NOT NULL,
    hypotheses          TEXT         NOT NULL,
    recommendations     TEXT         NOT NULL,
    raw_response        TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investigations_incident ON incident_investigations(incident_id);
CREATE INDEX idx_investigations_created_at ON incident_investigations(created_at DESC);
