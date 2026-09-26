-- ====================================================================
-- MedScan Enterprise — Migration V3 : Imagerie Médicale (MOD-05)
-- et Passerelle d'IA Clinique (MOD-06)
-- Conforme : RLS Multi-Tenant, Zero Trust, ISO 27001, DICOM Metadata
-- ====================================================================

-- 1. Table des Études / Examens d'Imagerie Médicale (MOD-05)
CREATE TABLE medscan.imaging_study (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE RESTRICT,
    patient_name VARCHAR(256) NOT NULL,
    modality VARCHAR(16) NOT NULL, -- XR, CR, DX, CT, MR, US
    body_part VARCHAR(64) NOT NULL, -- CHEST, ABDOMEN, PELVIS, EXTREMITY, SKULL
    title VARCHAR(256) NOT NULL,
    image_url TEXT NOT NULL,
    study_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    referring_doctor_id UUID NOT NULL,
    referring_doctor_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACQUIRED'
);

CREATE INDEX imaging_study_tenant_id_idx ON medscan.imaging_study (tenant_id);
CREATE INDEX imaging_study_patient_id_idx ON medscan.imaging_study (patient_id);
CREATE INDEX imaging_study_date_idx ON medscan.imaging_study (study_date DESC);

ALTER TABLE medscan.imaging_study ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.imaging_study FORCE ROW LEVEL SECURITY;

CREATE POLICY imaging_study_tenant_isolation ON medscan.imaging_study
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON medscan.imaging_study TO medscan_app;

-- 2. Résultats d'Inférence d'Intelligence Artificielle (MOD-06)
CREATE TABLE medscan.ai_analysis_result (
    id UUID PRIMARY KEY,
    study_id UUID NOT NULL REFERENCES medscan.imaging_study(id) ON DELETE CASCADE,
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    model_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    primary_finding TEXT NOT NULL,
    confidence_score NUMERIC(5,4) NOT NULL, -- e.g. 0.9460
    risk_level VARCHAR(32) NOT NULL, -- NORMAL, MILD, MODERATE, HIGH, CRITICAL
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    heatmap_overlay_url TEXT,
    execution_time_ms BIGINT NOT NULL,
    disclaimer TEXT NOT NULL
);

CREATE INDEX ai_analysis_study_id_idx ON medscan.ai_analysis_result (study_id);

ALTER TABLE medscan.ai_analysis_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.ai_analysis_result FORCE ROW LEVEL SECURITY;

CREATE POLICY ai_analysis_tenant_isolation ON medscan.ai_analysis_result
    FOR ALL
    TO medscan_app
    USING (
        EXISTS (
            SELECT 1 FROM medscan.imaging_study s
            WHERE s.id = study_id AND s.tenant_id = medscan.current_tenant_id()
        )
    );

GRANT SELECT, INSERT ON medscan.ai_analysis_result TO medscan_app;

-- 3. Compte-Rendu Médical & Validation Humaine (MOD-05 & Déontologie Médicale)
CREATE TABLE medscan.imaging_report (
    id UUID PRIMARY KEY,
    study_id UUID NOT NULL REFERENCES medscan.imaging_study(id) ON DELETE CASCADE,
    doctor_id UUID NOT NULL,
    doctor_name VARCHAR(128) NOT NULL,
    doctor_role VARCHAR(64) NOT NULL, -- RADIOLOGIST, DOCTOR
    validated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    conclusion TEXT NOT NULL,
    ai_agreement_status VARCHAR(32) NOT NULL, -- AGREED, MODIFIED, REJECTED
    recommended_actions TEXT
);

CREATE INDEX imaging_report_study_id_idx ON medscan.imaging_report (study_id);

ALTER TABLE medscan.imaging_report ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.imaging_report FORCE ROW LEVEL SECURITY;

CREATE POLICY imaging_report_tenant_isolation ON medscan.imaging_report
    FOR ALL
    TO medscan_app
    USING (
        EXISTS (
            SELECT 1 FROM medscan.imaging_study s
            WHERE s.id = study_id AND s.tenant_id = medscan.current_tenant_id()
        )
    );

GRANT SELECT, INSERT, UPDATE ON medscan.imaging_report TO medscan_app;
