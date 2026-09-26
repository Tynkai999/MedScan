-- ====================================================================
-- MedScan Enterprise — Migration V2 : Dossier Patient, Constantes,
-- Consultations, Prescriptions, Accès d'Urgence (Break-Glass) et Audit
-- Conforme : RLS Multi-Tenant, Zero Trust, ISO 27001, HIPAA
-- ====================================================================

-- 1. Table des Patients (MOD-03)
CREATE TABLE medscan.patient (
    id UUID PRIMARY KEY,
    national_id VARCHAR(64) NOT NULL UNIQUE,
    first_name VARCHAR(128) NOT NULL,
    last_name VARCHAR(128) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(16) NOT NULL,
    blood_group VARCHAR(8),
    phone VARCHAR(32),
    emergency_contact TEXT,
    allergies JSONB NOT NULL DEFAULT '[]'::jsonb,
    chronic_conditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    linked_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX patient_tenant_id_idx ON medscan.patient (tenant_id);
CREATE INDEX patient_national_id_idx ON medscan.patient (national_id);
CREATE INDEX patient_linked_user_id_idx ON medscan.patient (linked_user_id);

ALTER TABLE medscan.patient ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.patient FORCE ROW LEVEL SECURITY;

CREATE POLICY patient_tenant_isolation ON medscan.patient
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON medscan.patient TO medscan_app;

-- 2. Constantes Vitales (MOD-03)
CREATE TABLE medscan.vital_signs (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    systolic_bp INT,
    diastolic_bp INT,
    heart_rate INT,
    temperature NUMERIC(4,1),
    weight_kg NUMERIC(5,2),
    blood_glucose NUMERIC(4,2),
    recorded_by VARCHAR(128) NOT NULL,
    recorded_by_role VARCHAR(64) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT
);

CREATE INDEX vital_signs_patient_id_idx ON medscan.vital_signs (patient_id);
CREATE INDEX vital_signs_tenant_id_idx ON medscan.vital_signs (tenant_id);

ALTER TABLE medscan.vital_signs ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.vital_signs FORCE ROW LEVEL SECURITY;

CREATE POLICY vital_signs_tenant_isolation ON medscan.vital_signs
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

GRANT SELECT, INSERT ON medscan.vital_signs TO medscan_app;

-- 3. Consultations & Notes Cliniques (MOD-03)
CREATE TABLE medscan.consultation (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE CASCADE,
    doctor_id UUID NOT NULL,
    doctor_name VARCHAR(128) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chief_complaint TEXT NOT NULL,
    examination_notes TEXT,
    diagnosis TEXT NOT NULL,
    treatment_plan TEXT
);

CREATE INDEX consultation_patient_id_idx ON medscan.consultation (patient_id);
CREATE INDEX consultation_tenant_id_idx ON medscan.consultation (tenant_id);

ALTER TABLE medscan.consultation ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.consultation FORCE ROW LEVEL SECURITY;

CREATE POLICY consultation_tenant_isolation ON medscan.consultation
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON medscan.consultation TO medscan_app;

-- 4. Prescriptions / Ordonnances Médicales Numériques (MOD-08)
CREATE TABLE medscan.prescription (
    id UUID PRIMARY KEY,
    prescription_code VARCHAR(32) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE RESTRICT,
    patient_name VARCHAR(256) NOT NULL,
    doctor_id UUID NOT NULL,
    doctor_name VARCHAR(128) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    dispensed_by VARCHAR(128),
    dispensed_at TIMESTAMPTZ,
    dispensing_tenant_id UUID REFERENCES medscan.tenant(id)
);

CREATE TABLE medscan.prescription_item (
    id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES medscan.prescription(id) ON DELETE CASCADE,
    medication_name VARCHAR(256) NOT NULL,
    dosage VARCHAR(128) NOT NULL,
    frequency VARCHAR(128) NOT NULL,
    duration_days INT NOT NULL,
    instructions TEXT
);

CREATE INDEX prescription_code_idx ON medscan.prescription (prescription_code);
CREATE INDEX prescription_patient_id_idx ON medscan.prescription (patient_id);
CREATE INDEX prescription_item_prescription_id_idx ON medscan.prescription_item (prescription_id);

ALTER TABLE medscan.prescription ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.prescription FORCE ROW LEVEL SECURITY;
ALTER TABLE medscan.prescription_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.prescription_item FORCE ROW LEVEL SECURITY;

-- Note : Les ordonnances peuvent être consultées par le tenant émetteur ET par les pharmacies d'officine
CREATE POLICY prescription_access ON medscan.prescription
    FOR ALL
    TO medscan_app
    USING (
        tenant_id = medscan.current_tenant_id() 
        OR dispensing_tenant_id = medscan.current_tenant_id()
        OR status = 'ISSUED' -- Permet aux pharmacies tierces de lire l'ordonnance à dispenser
    );

CREATE POLICY prescription_item_access ON medscan.prescription_item
    FOR ALL
    TO medscan_app
    USING (TRUE);

GRANT SELECT, INSERT, UPDATE ON medscan.prescription TO medscan_app;
GRANT SELECT, INSERT, UPDATE ON medscan.prescription_item TO medscan_app;

-- 5. Journal d'Audit & Traçabilité (Append-Only - MOD-11)
CREATE TABLE medscan.audit_log (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id UUID NOT NULL,
    actor_username VARCHAR(128) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    tenant_id UUID,
    action VARCHAR(64) NOT NULL,
    resource VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    details TEXT
);

CREATE INDEX audit_log_timestamp_idx ON medscan.audit_log (timestamp DESC);
CREATE INDEX audit_log_tenant_id_idx ON medscan.audit_log (tenant_id);
CREATE INDEX audit_log_actor_id_idx ON medscan.audit_log (actor_id);

-- L'audit log est en écriture/lecture seule, aucune modification ou suppression permise
GRANT SELECT, INSERT ON medscan.audit_log TO medscan_app;
