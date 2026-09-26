#!/usr/bin/env sh
set -eu

: "${MEDSCAN_DB_APP_PASSWORD:?MEDSCAN_DB_APP_PASSWORD must be set}"

psql --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set "app_password=${MEDSCAN_DB_APP_PASSWORD}" <<'SQL'
SELECT format(
    'CREATE ROLE medscan_app LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
    :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medscan_app')
\gexec

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database())
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO medscan_app', current_database())
\gexec
SQL
