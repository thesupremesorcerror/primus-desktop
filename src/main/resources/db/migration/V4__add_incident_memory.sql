CREATE TABLE incident_memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID REFERENCES incidents(id),
    summary TEXT NOT NULL,
    root_cause TEXT,
    remediation_taken TEXT,
    outcome TEXT,
    embedding TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);