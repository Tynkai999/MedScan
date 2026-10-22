-- =====================================================================
-- Migration V8 : Module de Synchronisation Hors-Ligne & Idempotence (MOD-10)
-- Traçabilité des opérations de terrain synchronisées, déduplication stricte
-- Conforme : RLS Multi-Tenant, Zero Trust, Offline-First Architecture
-- =====================================================================

CREATE TABLE IF NOT EXISTS medscan.sync_idempotency_log (
    operation_id UUID PRIMARY KEY,
    batch_id UUID,
    device_id VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN_DEVICE',
    actor_id UUID NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    entity_type VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL DEFAULT 'CREATE',
    entity_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    details TEXT
);

CREATE INDEX IF NOT EXISTS sync_idempotency_tenant_idx ON medscan.sync_idempotency_log (tenant_id);
CREATE INDEX IF NOT EXISTS sync_idempotency_actor_idx ON medscan.sync_idempotency_log (actor_id);
CREATE INDEX IF NOT EXISTS sync_idempotency_processed_at_idx ON medscan.sync_idempotency_log (processed_at DESC);
CREATE INDEX IF NOT EXISTS sync_idempotency_entity_idx ON medscan.sync_idempotency_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS sync_idempotency_batch_idx ON medscan.sync_idempotency_log (batch_id);

COMMENT ON TABLE medscan.sync_idempotency_log IS 'Journal des opérations cliniques synchronisées hors-ligne avec déduplication idempotente (MOD-10)';
COMMENT ON COLUMN medscan.sync_idempotency_log.operation_id IS 'UUID client garantissant un traitement strictement idempotent (aucun doublon lors des réessais réseau)';
COMMENT ON COLUMN medscan.sync_idempotency_log.status IS 'APPLIED, DUPLICATE_IGNORED, REJECTED, CONFLICT_RESOLVED';

ALTER TABLE medscan.sync_idempotency_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.sync_idempotency_log FORCE ROW LEVEL SECURITY;

CREATE POLICY sync_idempotency_tenant_isolation ON medscan.sync_idempotency_log
    FOR ALL
    TO medscan_app
    USING (
        tenant_id = medscan.current_tenant_id()
    )
    WITH CHECK (
        tenant_id = medscan.current_tenant_id()
    );
