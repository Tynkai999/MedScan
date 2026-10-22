-- =====================================================================
-- Migration V9 : Hub d'Interopérabilité Internationale HL7 FHIR R4 (MOD-07)
-- Mappings canoniques de ressources et traçabilité des exports FHIR
-- Conforme : Standard HL7 FHIR R4 (4.0.1), RLS Multi-Tenant, Zero Trust
-- =====================================================================

CREATE TABLE IF NOT EXISTS medscan.fhir_resource_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    internal_entity_type VARCHAR(64) NOT NULL,
    internal_entity_id UUID NOT NULL,
    fhir_resource_type VARCHAR(64) NOT NULL,
    fhir_canonical_uri TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS fhir_mapping_tenant_idx ON medscan.fhir_resource_mapping (tenant_id);
CREATE INDEX IF NOT EXISTS fhir_mapping_internal_idx ON medscan.fhir_resource_mapping (internal_entity_type, internal_entity_id);
CREATE INDEX IF NOT EXISTS fhir_mapping_resource_type_idx ON medscan.fhir_resource_mapping (fhir_resource_type, internal_entity_id);

COMMENT ON TABLE medscan.fhir_resource_mapping IS 'Correspondance canonique entre entités internes MedScan et identifiants de ressources HL7 FHIR R4 (MOD-07)';
COMMENT ON COLUMN medscan.fhir_resource_mapping.internal_entity_type IS 'Type d entité interne : PATIENT, OBSERVATION, MEDICATION_REQUEST';
COMMENT ON COLUMN medscan.fhir_resource_mapping.fhir_resource_type IS 'Type de ressource FHIR R4 : Patient, Observation, MedicationRequest, Bundle';

ALTER TABLE medscan.fhir_resource_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.fhir_resource_mapping FORCE ROW LEVEL SECURITY;

CREATE POLICY fhir_mapping_tenant_isolation ON medscan.fhir_resource_mapping
    FOR ALL
    TO medscan_app
    USING (
        tenant_id = medscan.current_tenant_id()
    )
    WITH CHECK (
        tenant_id = medscan.current_tenant_id()
    );

CREATE TABLE IF NOT EXISTS medscan.fhir_export_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL,
    patient_id UUID REFERENCES medscan.patient(id) ON DELETE SET NULL,
    export_type VARCHAR(64) NOT NULL,
    bundle_id UUID,
    resource_count INTEGER NOT NULL DEFAULT 1,
    exported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    client_user_agent TEXT
);

CREATE INDEX IF NOT EXISTS fhir_export_tenant_idx ON medscan.fhir_export_log (tenant_id);
CREATE INDEX IF NOT EXISTS fhir_export_actor_idx ON medscan.fhir_export_log (actor_id);
CREATE INDEX IF NOT EXISTS fhir_export_patient_idx ON medscan.fhir_export_log (patient_id);
CREATE INDEX IF NOT EXISTS fhir_export_exported_at_idx ON medscan.fhir_export_log (exported_at DESC);

COMMENT ON TABLE medscan.fhir_export_log IS 'Journal immuable d audit des requêtes et extractions HL7 FHIR R4 (MOD-07)';
COMMENT ON COLUMN medscan.fhir_export_log.export_type IS 'Type d export : PATIENT_BUNDLE_EXPORT, OBSERVATIONS_SEARCH, PATIENT_READ, MEDICATION_REQUEST_READ';

ALTER TABLE medscan.fhir_export_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.fhir_export_log FORCE ROW LEVEL SECURITY;

CREATE POLICY fhir_export_tenant_isolation ON medscan.fhir_export_log
    FOR ALL
    TO medscan_app
    USING (
        tenant_id = medscan.current_tenant_id()
    )
    WITH CHECK (
        tenant_id = medscan.current_tenant_id()
    );
