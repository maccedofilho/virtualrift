#!/usr/bin/env bash

set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
validator="$script_dir/validate-cloud-sql-backups.sh"
gke_validator="$script_dir/validate-gke-private-access.sh"
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

cat >"$test_dir/gcloud" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $1 == sql && $2 == instances && $3 == describe ]]; then
  printf '%s\n' "${MOCK_INSTANCE_JSON:?}"
elif [[ $1 == sql && $2 == backups && $3 == list ]]; then
  printf '%s\n' "${MOCK_BACKUP_JSON:?}"
elif [[ $1 == container && $2 == clusters && $3 == describe ]]; then
  printf '%s\n' "${MOCK_CLUSTER_JSON:?}"
elif [[ $1 == container && $2 == fleet && $3 == memberships && $4 == describe ]]; then
  printf '%s\n' "${MOCK_MEMBERSHIP_JSON:?}"
else
  echo "Unexpected gcloud invocation: $*" >&2
  exit 1
fi
EOF
chmod +x "$test_dir/gcloud"
export PATH="$test_dir:$PATH"

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
export MOCK_INSTANCE_JSON='{
  "state": "RUNNABLE",
  "settings": {
    "availabilityType": "REGIONAL",
    "deletionProtectionEnabled": true,
    "backupConfiguration": {
      "enabled": true,
      "pointInTimeRecoveryEnabled": true,
      "transactionLogRetentionDays": 7,
      "backupRetentionSettings": {"retentionUnit": "COUNT", "retainedBackups": 30}
    }
  }
}'
export MOCK_BACKUP_JSON="[{\"endTime\":\"$now\",\"type\":\"AUTOMATED\"}]"
export MOCK_CLUSTER_JSON='{
  "status": "RUNNING",
  "privateClusterConfig": {"enablePrivateNodes": true, "enablePrivateEndpoint": true}
}'
export MOCK_MEMBERSHIP_JSON='{"state":{"code":"READY"}}'

"$validator" project instance production 30 >/dev/null
"$gke_validator" project cluster region membership >/dev/null

MOCK_INSTANCE_JSON=$(jq '.settings.backupConfiguration.backupRetentionSettings.retainedBackups = 7' \
  <<<"$MOCK_INSTANCE_JSON")
export MOCK_INSTANCE_JSON
if "$validator" project instance production 30 >/dev/null 2>&1; then
  echo "Production backup gate accepted insufficient retention." >&2
  exit 1
fi

MOCK_CLUSTER_JSON=$(jq '.privateClusterConfig.enablePrivateEndpoint = false' <<<"$MOCK_CLUSTER_JSON")
export MOCK_CLUSTER_JSON
if "$gke_validator" project cluster region membership >/dev/null 2>&1; then
  echo "GKE private-access gate accepted a public control-plane endpoint." >&2
  exit 1
fi

MOCK_INSTANCE_JSON=$(jq '.settings.backupConfiguration.backupRetentionSettings.retainedBackups = 30' \
  <<<"$MOCK_INSTANCE_JSON")
export MOCK_INSTANCE_JSON
export MOCK_BACKUP_JSON='[{"endTime":"2000-01-01T00:00:00Z"}]'
if "$validator" project instance production 30 >/dev/null 2>&1; then
  echo "Backup gate accepted a stale backup." >&2
  exit 1
fi

echo "Resilience gate tests passed."
