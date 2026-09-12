CREATE TABLE remediation_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID REFERENCES incidents(id),
    action_type TEXT NOT NULL,
    service_name TEXT NOT NULL,
    risk_level TEXT NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    expected_recovery_pct NUMERIC(5,2),
    rationale TEXT,
    recommended BOOLEAN DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);