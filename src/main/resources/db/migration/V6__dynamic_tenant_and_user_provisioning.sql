-- =====================================================================
-- Migration V6 : Structures de Santé Dynamiques (Tenants) & Gestion du Personnel
-- MOD-01 (Multi-Tenancy Dynamique) & MOD-02 (IAM & RBAC Provisioning)
-- Conforme : Zero Hardcoded Identities, RLS Multi-Tenant, Zero Trust
-- =====================================================================

-- 1. Enrichissement de la table medscan.tenant
ALTER TABLE medscan.tenant
    ADD COLUMN IF NOT EXISTS type VARCHAR(64) NOT NULL DEFAULT 'CLINIC',
    ADD COLUMN IF NOT EXISTS country VARCHAR(128) NOT NULL DEFAULT 'Burkina Faso',
    ADD COLUMN IF NOT EXISTS city VARCHAR(128) NOT NULL DEFAULT 'Ouagadougou',
    ADD COLUMN IF NOT EXISTS phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email VARCHAR(128),
    ADD COLUMN IF NOT EXISTS address TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

COMMENT ON COLUMN medscan.tenant.type IS 'Type de structure (HOSPITAL, CLINIC, PHARMACY, LABORATORY, LOGISTICS, PLATFORM, etc.)';
COMMENT ON COLUMN medscan.tenant.country IS 'Pays d implantation de l établissement';
COMMENT ON COLUMN medscan.tenant.city IS 'Ville d implantation';
COMMENT ON COLUMN medscan.tenant.status IS 'Statut opérationnel (ACTIVE, SUSPENDED, PENDING)';

-- 2. Table des comptes du personnel et acteurs de santé
CREATE TABLE IF NOT EXISTS medscan.user_account (
    user_id UUID PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    tenant_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    roles TEXT[] NOT NULL DEFAULT '{}',
    permissions TEXT[] NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS user_account_tenant_id_idx ON medscan.user_account (tenant_id);
CREATE INDEX IF NOT EXISTS user_account_username_idx ON medscan.user_account (username);

ALTER TABLE medscan.user_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.user_account FORCE ROW LEVEL SECURITY;

CREATE POLICY user_account_tenant_isolation ON medscan.user_account
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON medscan.user_account TO medscan_app;
