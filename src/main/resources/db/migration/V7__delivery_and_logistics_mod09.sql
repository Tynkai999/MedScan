-- =====================================================================
-- Migration V7 : Module Logistique & Livraison Sécurisée (MOD-09)
-- Suivi d'acheminement des colis pharmaceutiques scellés et validation OTP
-- Conforme : RLS Multi-Tenant (Pharmacie & Transporteur), Zero Trust, Sécurité OTP
-- =====================================================================

CREATE TABLE IF NOT EXISTS medscan.delivery_order (
    id UUID PRIMARY KEY,
    delivery_code VARCHAR(64) NOT NULL UNIQUE,
    prescription_code VARCHAR(64),
    prescription_id UUID,
    patient_id UUID NOT NULL,
    patient_name VARCHAR(256) NOT NULL,
    patient_phone VARCHAR(64) NOT NULL,
    delivery_address TEXT NOT NULL,
    destination_city VARCHAR(128) NOT NULL DEFAULT 'Ouagadougou',
    pharmacy_tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    pharmacy_name VARCHAR(256) NOT NULL,
    logistics_tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    delivery_agent_id UUID,
    delivery_agent_name VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    package_description TEXT,
    special_instructions TEXT,
    otp_hash VARCHAR(256) NOT NULL,
    otp_salt VARCHAR(128) NOT NULL,
    otp_expires_at TIMESTAMPTZ NOT NULL,
    otp_attempts_remaining INT NOT NULL DEFAULT 3,
    otp_clear VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at TIMESTAMPTZ,
    picked_up_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    recipient_confirmation VARCHAR(256),
    failure_reason TEXT
);

CREATE INDEX IF NOT EXISTS delivery_order_patient_idx ON medscan.delivery_order (patient_id);
CREATE INDEX IF NOT EXISTS delivery_order_pharmacy_idx ON medscan.delivery_order (pharmacy_tenant_id);
CREATE INDEX IF NOT EXISTS delivery_order_logistics_idx ON medscan.delivery_order (logistics_tenant_id);
CREATE INDEX IF NOT EXISTS delivery_order_agent_idx ON medscan.delivery_order (delivery_agent_id);
CREATE INDEX IF NOT EXISTS delivery_order_status_idx ON medscan.delivery_order (status);
CREATE INDEX IF NOT EXISTS delivery_order_created_at_idx ON medscan.delivery_order (created_at DESC);

COMMENT ON TABLE medscan.delivery_order IS 'Commandes de livraison pharmaceutique sécurisée avec preuve OTP (MOD-09)';
COMMENT ON COLUMN medscan.delivery_order.status IS 'CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, CANCELLED';
COMMENT ON COLUMN medscan.delivery_order.otp_hash IS 'Empreinte SHA-256 du code OTP salé pour validation inviolable';

ALTER TABLE medscan.delivery_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.delivery_order FORCE ROW LEVEL SECURITY;

CREATE POLICY delivery_order_tenant_isolation ON medscan.delivery_order
    FOR ALL
    TO medscan_app
    USING (
        pharmacy_tenant_id = medscan.current_tenant_id()
        OR logistics_tenant_id = medscan.current_tenant_id()
    )
    WITH CHECK (
        pharmacy_tenant_id = medscan.current_tenant_id()
        OR logistics_tenant_id = medscan.current_tenant_id()
    );

GRANT SELECT, INSERT, UPDATE ON medscan.delivery_order TO medscan_app;
