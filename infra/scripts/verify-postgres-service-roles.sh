#!/usr/bin/env bash

set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER must be a PostgreSQL administrator}"
: "${PGPASSWORD:?PGPASSWORD is required}"

PGPORT="${PGPORT:-5432}"
PGSSLMODE="${PGSSLMODE:-verify-full}"
export PGPORT PGSSLMODE

services=(auth tenant orchestrator reports)
failed=0

for service in "${services[@]}"; do
  database="virtualrift_${service}"
  app_role="${database}"
  migrator_role="${database}_migrator"
  flyway_table="${service}_flyway_schema_history"

  role_check="$(psql -X -At -v ON_ERROR_STOP=1 -d postgres -v "app_role=${app_role}" -v "migrator_role=${migrator_role}" <<'SQL'
SELECT count(*)
FROM pg_roles
WHERE rolname IN (:'app_role', :'migrator_role')
  AND NOT rolsuper
  AND NOT rolcreatedb
  AND NOT rolcreaterole
  AND NOT rolreplication
  AND NOT rolbypassrls;
SQL
)"

  runtime_privilege_violations="$(psql -X -At -v ON_ERROR_STOP=1 -d "${database}" -v "app_role=${app_role}" <<'SQL'
SELECT
    has_schema_privilege(:'app_role', 'public', 'CREATE')::integer
    + has_database_privilege(:'app_role', current_database(), 'TEMP')::integer
    + EXISTS (
        SELECT 1
        FROM pg_roles candidate
        WHERE (
            candidate.rolsuper
            OR candidate.rolcreatedb
            OR candidate.rolcreaterole
            OR candidate.rolreplication
            OR candidate.rolbypassrls
        )
        AND pg_has_role(:'app_role', candidate.oid, 'MEMBER')
    )::integer
    + EXISTS (
        SELECT 1
        FROM pg_class relation
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'public'
          AND relation.relkind IN ('r', 'p')
          AND relation.relrowsecurity
          AND NOT relation.relforcerowsecurity
          AND pg_has_role(:'app_role', relation.relowner, 'MEMBER')
    )::integer;
SQL
)"

  rls_without_policy="$(psql -X -At -v ON_ERROR_STOP=1 -d "${database}" <<'SQL'
SELECT count(*)
FROM pg_class relation
JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
WHERE namespace.nspname = 'public'
  AND relation.relrowsecurity
  AND NOT EXISTS (SELECT 1 FROM pg_policy policy WHERE policy.polrelid = relation.oid);
SQL
)"

  flyway_privilege_violations="$(psql -X -At -v ON_ERROR_STOP=1 -d "${database}" \
    -v "app_role=${app_role}" -v "flyway_table=${flyway_table}" <<'SQL'
SELECT CASE
    WHEN to_regclass('public.' || :'flyway_table') IS NULL THEN 1
    WHEN has_table_privilege(:'app_role', format('public.%I', :'flyway_table'), 'SELECT')
      OR has_table_privilege(:'app_role', format('public.%I', :'flyway_table'), 'INSERT')
      OR has_table_privilege(:'app_role', format('public.%I', :'flyway_table'), 'UPDATE')
      OR has_table_privilege(:'app_role', format('public.%I', :'flyway_table'), 'DELETE') THEN 1
    ELSE 0
END;
SQL
)"

  if [[ "${role_check}" != "2" || "${runtime_privilege_violations}" != "0" \
    || "${rls_without_policy}" != "0" || "${flyway_privilege_violations}" != "0" ]]; then
    printf 'FAILED %-28s roles=%s runtime_privilege_violations=%s rls_without_policy=%s flyway_privileges=%s\n' \
      "${database}" "${role_check}" "${runtime_privilege_violations}" "${rls_without_policy}" \
      "${flyway_privilege_violations}" >&2
    failed=1
  else
    printf 'OK     %-28s least privilege and RLS metadata verified\n' "${database}"
  fi
done

exit "${failed}"
