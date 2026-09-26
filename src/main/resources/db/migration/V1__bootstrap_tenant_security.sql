DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medscan_app') THEN
        CREATE ROLE medscan_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
    END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS medscan AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA medscan FROM PUBLIC;
GRANT USAGE ON SCHEMA medscan TO medscan_app;

CREATE FUNCTION medscan.current_tenant_id()
RETURNS UUID
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('medscan.tenant_id', true), '')::UUID
$$;

REVOKE ALL ON FUNCTION medscan.current_tenant_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION medscan.current_tenant_id() TO medscan_app;

CREATE TABLE medscan.tenant (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenant_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT tenant_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE medscan.tenant_security_probe (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES medscan.tenant(id) ON DELETE RESTRICT,
    label TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenant_security_probe_label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT tenant_security_probe_tenant_label_unique UNIQUE (tenant_id, label)
);

CREATE INDEX tenant_security_probe_tenant_id_idx
    ON medscan.tenant_security_probe (tenant_id);

ALTER TABLE medscan.tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.tenant FORCE ROW LEVEL SECURITY;
ALTER TABLE medscan.tenant_security_probe ENABLE ROW LEVEL SECURITY;
ALTER TABLE medscan.tenant_security_probe FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_self_read
    ON medscan.tenant
    FOR SELECT
    TO medscan_app
    USING (id = medscan.current_tenant_id());

CREATE POLICY tenant_isolation
    ON medscan.tenant_security_probe
    FOR ALL
    TO medscan_app
    USING (tenant_id = medscan.current_tenant_id())
    WITH CHECK (tenant_id = medscan.current_tenant_id());

REVOKE ALL ON ALL TABLES IN SCHEMA medscan FROM PUBLIC;
GRANT SELECT ON medscan.tenant TO medscan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON medscan.tenant_security_probe TO medscan_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA medscan REVOKE ALL ON TABLES FROM PUBLIC;
