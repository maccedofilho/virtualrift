#!/usr/bin/env bash

set -euo pipefail

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-virtualrift-postgres}"
POSTGRES_USER="${POSTGRES_USER:-virtualrift}"
POSTGRES_DB="${POSTGRES_DB:-postgres}"

databases=(
  "virtualrift_auth"
  "virtualrift_tenant"
  "virtualrift_orchestrator"
  "virtualrift_reports"
)

for db in "${databases[@]}"; do
  exists="$(
    docker exec "${POSTGRES_CONTAINER}" \
      psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc \
      "SELECT 1 FROM pg_database WHERE datname='${db}'"
  )"

  if [[ "${exists}" != "1" ]]; then
    docker exec "${POSTGRES_CONTAINER}" \
      psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
      -c "CREATE DATABASE ${db}"
  fi
done
