#!/usr/bin/env bash

set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER must be a PostgreSQL administrator}"
: "${PGPASSWORD:?PGPASSWORD is required}"

PGPORT="${PGPORT:-5432}"
PGSSLMODE="${PGSSLMODE:-verify-full}"
export PGPORT PGSSLMODE

services=(auth tenant orchestrator reports)

password_for() {
  local service="$1"
  local kind="$2"
  local service_name
  local kind_name
  service_name="$(printf '%s' "${service}" | tr '[:lower:]' '[:upper:]')"
  kind_name="$(printf '%s' "${kind}" | tr '[:lower:]' '[:upper:]')"
  local variable="VIRTUALRIFT_${service_name}_${kind_name}_DB_PASSWORD"
  if [[ -z "${!variable:-}" ]]; then
    printf 'Missing required secret: %s\n' "${variable}" >&2
    exit 1
  fi
  printf '%s' "${!variable}"
}

for service in "${services[@]}"; do
  database="virtualrift_${service}"
  app_role="${database}"
  migrator_role="${database}_migrator"
  flyway_table="${service}_flyway_schema_history"
  app_password="$(password_for "${service}" runtime)"
  migrator_password="$(password_for "${service}" migration)"

  psql -X -v ON_ERROR_STOP=1 -d postgres \
    -v "database=${database}" \
    -v "app_role=${app_role}" \
    -v "app_password=${app_password}" \
    -v "migrator_role=${migrator_role}" \
    -v "migrator_password=${migrator_password}" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
    :'app_role', :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_role')
\gexec
SELECT format(
    'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
    :'app_role', :'app_password'
)
\gexec
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
    :'migrator_role', :'migrator_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migrator_role')
\gexec
SELECT format(
    'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
    :'migrator_role', :'migrator_password'
)
\gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'database', :'migrator_role')
WHERE EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database')
\gexec
SQL

  psql -X -v ON_ERROR_STOP=1 -d "${database}" \
    -v "database=${database}" \
    -v "app_role=${app_role}" \
    -v "migrator_role=${migrator_role}" \
    -v "flyway_table=${flyway_table}" <<'SQL'
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'migrator_role')
FROM pg_tables
WHERE schemaname = 'public'
\gexec
SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', sequence_schema, sequence_name, :'migrator_role')
FROM information_schema.sequences
WHERE sequence_schema = 'public'
\gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'migrator_role')
\gexec
SELECT format('REVOKE CONNECT, TEMPORARY ON DATABASE %I FROM PUBLIC', :'database')
\gexec
SELECT format('REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I', :'database', :'app_role')
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I, %I', :'database', :'app_role', :'migrator_role')
\gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM :"app_role";
GRANT USAGE ON SCHEMA public TO :"app_role";
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM :"app_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_role";
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM :"app_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
    REVOKE ALL PRIVILEGES ON TABLES FROM :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
    REVOKE ALL PRIVILEGES ON SEQUENCES FROM :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app_role";
SELECT format('REVOKE ALL ON TABLE public.%I FROM %I', :'flyway_table', :'app_role')
WHERE to_regclass('public.' || :'flyway_table') IS NOT NULL
\gexec
SQL
done

printf 'Production PostgreSQL runtime and migrator roles were provisioned.\n'
