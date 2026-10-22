-- =====================================================================
-- Migration V10 : Rendez-vous & Télésanté (MOD-12), Laboratoire d'Analyses (MOD-13),
-- Surveillance Épidémiologique DHIS2 (MOD-14), Messagerie & Alertes Multi-Entités (MOD-15)
-- Conforme : RLS Multi-Tenant, Zero Trust, Traçabilité DPO immuable
-- =====================================================================

-- 1. RENDEZ-VOUS & TÉLÉCONSULTATION (MOD-12)
CREATE TABLE IF NOT EXISTS medscan.appointment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE RESTRICT,
    patient_name VARCHAR(128) NOT NULL,
    practitioner_id UUID NOT NULL,
    practitioner_name VARCHAR(128) NOT NULL,
    practitioner_role VARCHAR(32) NOT NULL,
    appointment_type VARCHAR(64) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    reason TEXT,
    clinical_notes TEXT,
    teleconsultation_room_id VARCHAR(128),
    teleconsultation_join_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS appointment_tenant_idx ON medscan.appointment (tenant_id);
CREATE INDEX IF NOT EXISTS appointment_patient_idx ON medscan.appointment (patient_id);
CREATE INDEX IF NOT EXISTS appointment_practitioner_idx ON medscan.appointment (practitioner_id);
CREATE INDEX IF NOT EXISTS appointment_scheduled_idx ON medscan.appointment (scheduled_at);
CREATE INDEX IF NOT EXISTS appointment_status_idx ON medscan.appointment (status);

ALTER TABLE medscan.appointment ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.appointment FORCE ROW LEVEL SECURITY;

CREATE POLICY appointment_tenant_isolation ON medscan.appointment
    FOR ALL TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

-- 2. LABORATOIRE D'ANALYSES MÉDICALES (MOD-13)
CREATE TABLE IF NOT EXISTS medscan.lab_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code VARCHAR(64) UNIQUE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    patient_id UUID NOT NULL REFERENCES medscan.patient(id) ON DELETE RESTRICT,
    patient_name VARCHAR(128) NOT NULL,
    prescriber_id UUID NOT NULL,
    prescriber_name VARCHAR(128) NOT NULL,
    technician_id UUID,
    technician_name VARCHAR(128),
    test_category VARCHAR(64) NOT NULL,
    test_name VARCHAR(128) NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'ROUTINE',
    status VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    clinical_indication TEXT,
    sample_collected_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS lab_order_tenant_idx ON medscan.lab_order (tenant_id);
CREATE INDEX IF NOT EXISTS lab_order_patient_idx ON medscan.lab_order (patient_id);
CREATE INDEX IF NOT EXISTS lab_order_technician_idx ON medscan.lab_order (technician_id);
CREATE INDEX IF NOT EXISTS lab_order_status_idx ON medscan.lab_order (status);

ALTER TABLE medscan.lab_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.lab_order FORCE ROW LEVEL SECURITY;

CREATE POLICY lab_order_tenant_isolation ON medscan.lab_order
    FOR ALL TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

CREATE TABLE IF NOT EXISTS medscan.lab_result_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id UUID NOT NULL REFERENCES medscan.lab_order(id) ON DELETE CASCADE,
    param_code VARCHAR(64) NOT NULL,
    param_name VARCHAR(128) NOT NULL,
    loinc_code VARCHAR(32),
    numeric_value NUMERIC(10,3),
    text_value TEXT,
    unit VARCHAR(32),
    ref_interval_low NUMERIC(10,3),
    ref_interval_high NUMERIC(10,3),
    is_abnormal BOOLEAN DEFAULT false,
    is_critical BOOLEAN DEFAULT false,
    interpretation TEXT
);

CREATE INDEX IF NOT EXISTS lab_item_order_idx ON medscan.lab_result_item (lab_order_id);
CREATE INDEX IF NOT EXISTS lab_item_loinc_idx ON medscan.lab_result_item (loinc_code);

-- 3. SURVEILLANCE ÉPIDÉMIOLOGIQUE & NOTIFICATIONS OMS / DHIS2 (MOD-14)
CREATE TABLE IF NOT EXISTS medscan.epidemiological_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    region VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    disease_code VARCHAR(64) NOT NULL,
    disease_name VARCHAR(128) NOT NULL,
    detection_source VARCHAR(64) NOT NULL,
    patient_age INTEGER,
    patient_gender VARCHAR(8),
    outcome VARCHAR(32) NOT NULL DEFAULT 'UNDER_TREATMENT',
    reported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reported_by UUID NOT NULL,
    dhis2_tracked_entity_id VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS epi_case_tenant_idx ON medscan.epidemiological_case (tenant_id);
CREATE INDEX IF NOT EXISTS epi_case_disease_idx ON medscan.epidemiological_case (disease_code);
CREATE INDEX IF NOT EXISTS epi_case_region_idx ON medscan.epidemiological_case (region, district);
CREATE INDEX IF NOT EXISTS epi_case_reported_idx ON medscan.epidemiological_case (reported_at DESC);

ALTER TABLE medscan.epidemiological_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.epidemiological_case FORCE ROW LEVEL SECURITY;

CREATE POLICY epi_case_tenant_isolation ON medscan.epidemiological_case
    FOR ALL TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

-- 4. MESSAGERIE SÉCURISÉE CLINIQUE (MOD-15)
CREATE TABLE IF NOT EXISTS medscan.clinical_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    sender_id UUID NOT NULL,
    sender_name VARCHAR(128) NOT NULL,
    sender_role VARCHAR(32) NOT NULL,
    recipient_id UUID NOT NULL,
    recipient_name VARCHAR(128) NOT NULL,
    recipient_role VARCHAR(32) NOT NULL,
    patient_id UUID REFERENCES medscan.patient(id) ON DELETE SET NULL,
    subject VARCHAR(256),
    content TEXT NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'ROUTINE',
    read_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS message_conversation_idx ON medscan.clinical_message (conversation_id);
CREATE INDEX IF NOT EXISTS message_sender_idx ON medscan.clinical_message (sender_id);
CREATE INDEX IF NOT EXISTS message_recipient_idx ON medscan.clinical_message (recipient_id);
CREATE INDEX IF NOT EXISTS message_tenant_idx ON medscan.clinical_message (tenant_id);

ALTER TABLE medscan.clinical_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.clinical_message FORCE ROW LEVEL SECURITY;

CREATE POLICY message_tenant_isolation ON medscan.clinical_message
    FOR ALL TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

-- 5. NOTIFICATIONS & ALERTES MULTI-ENTITÉS (MOD-15)
CREATE TABLE IF NOT EXISTS medscan.in_app_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    recipient_user_id UUID NOT NULL,
    recipient_role VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO',
    title VARCHAR(256) NOT NULL,
    message TEXT NOT NULL,
    reference_entity_type VARCHAR(64),
    reference_entity_id VARCHAR(128),
    is_read BOOLEAN NOT NULL DEFAULT false,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS notification_user_idx ON medscan.in_app_notification (recipient_user_id, is_read);
CREATE INDEX IF NOT EXISTS notification_tenant_idx ON medscan.in_app_notification (tenant_id);
CREATE INDEX IF NOT EXISTS notification_created_idx ON medscan.in_app_notification (created_at DESC);

ALTER TABLE medscan.in_app_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.in_app_notification FORCE ROW LEVEL SECURITY;

CREATE POLICY notification_tenant_isolation ON medscan.in_app_notification
    FOR ALL TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());
