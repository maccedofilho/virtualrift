#!/usr/bin/env bash

set -Eeuo pipefail

project_id=${1:-}
source_instance=${2:-}
confirmation=${3:-}

if [[ -z $project_id || -z $source_instance ]]; then
  echo "Usage: $0 <project-id> <source-instance> RESTORE" >&2
  exit 1
fi

if [[ $confirmation != RESTORE ]]; then
  echo "Point-in-time restore drill requires the exact RESTORE confirmation." >&2
  exit 1
fi

for command_name in gcloud jq date; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

restore_time=$(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%SZ)
drill_suffix=$(date -u +%Y%m%d%H%M%S)
drill_instance=$(printf 'vr-drill-%s-%s' "$source_instance" "$drill_suffix" | cut -c1-98)
created=0

cleanup() {
  local exit_status=$?
  trap - EXIT

  if ((created == 1)); then
    echo "Removing temporary restore instance $drill_instance."
    gcloud sql instances patch "$drill_instance" \
      --project "$project_id" --no-deletion-protection --quiet || true
    gcloud sql instances delete "$drill_instance" \
      --project "$project_id" --quiet || true
  fi

  exit "$exit_status"
}
trap cleanup EXIT

echo "Cloning $source_instance at $restore_time into temporary instance $drill_instance."
created=1
gcloud sql instances clone "$source_instance" "$drill_instance" \
  --project "$project_id" \
  --point-in-time "$restore_time" \
  --quiet

restored_json=$(gcloud sql instances describe "$drill_instance" \
  --project "$project_id" --format=json)
if ! jq -e --arg drill "$drill_instance" '
  .state == "RUNNABLE" and
  .databaseVersion == "POSTGRES_16" and
  .name == $drill
' <<<"$restored_json" >/dev/null; then
  echo "Temporary PITR instance did not become a runnable PostgreSQL 16 instance." >&2
  exit 1
fi

database_count=$(gcloud sql databases list \
  --instance "$drill_instance" \
  --project "$project_id" \
  --format=json | jq '[.[] | select(.name | startswith("virtualrift_"))] | length')
if ((database_count < 4)); then
  echo "Restore drill found only $database_count VirtualRift databases; expected at least 4." >&2
  exit 1
fi

echo "Cloud SQL PITR drill passed for $source_instance at $restore_time."
