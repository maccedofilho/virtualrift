#!/usr/bin/env bash

set -Eeuo pipefail

project_id=${1:-}
instance_name=${2:-}
environment=${3:-}
max_backup_age_hours=${4:-30}

if [[ -z $project_id || -z $instance_name ]]; then
  echo "Usage: $0 <project-id> <instance-name> <dev|staging|production> [max-backup-age-hours]" >&2
  exit 1
fi

case "$environment" in
  dev | staging | production) ;;
  *)
    echo "Environment must be dev, staging or production." >&2
    exit 1
    ;;
esac

if [[ ! $max_backup_age_hours =~ ^[1-9][0-9]*$ ]]; then
  echo "Maximum backup age must be a positive integer number of hours." >&2
  exit 1
fi

for command_name in gcloud jq date; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

instance_json=$(gcloud sql instances describe "$instance_name" \
  --project "$project_id" --format=json)

minimum_retained_backups=7
if [[ $environment == staging ]]; then
  minimum_retained_backups=14
elif [[ $environment == production ]]; then
  minimum_retained_backups=30
fi

if ! jq -e \
  --arg environment "$environment" \
  --argjson minimum "$minimum_retained_backups" '
    .state == "RUNNABLE" and
    .settings.backupConfiguration.enabled == true and
    .settings.backupConfiguration.pointInTimeRecoveryEnabled == true and
    .settings.backupConfiguration.transactionLogRetentionDays == 7 and
    .settings.backupConfiguration.backupRetentionSettings.retentionUnit == "COUNT" and
    .settings.backupConfiguration.backupRetentionSettings.retainedBackups >= $minimum and
    ($environment == "dev" or .settings.availabilityType == "REGIONAL") and
    ($environment == "dev" or .settings.deletionProtectionEnabled == true)
  ' <<<"$instance_json" >/dev/null; then
  echo "Cloud SQL instance does not satisfy the $environment resilience contract." >&2
  exit 1
fi

backup_json=$(gcloud sql backups list \
  --instance "$instance_name" \
  --project "$project_id" \
  --filter='status=SUCCESSFUL AND type=AUTOMATED' \
  --sort-by='~endTime' \
  --limit=1 \
  --format=json)

latest_end_time=$(jq -er '.[0].endTime' <<<"$backup_json") || {
  echo "No successful automated backup was found for $instance_name." >&2
  exit 1
}

latest_epoch=$(jq -nr --arg value "$latest_end_time" \
  '$value | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601')
now_epoch=$(date -u +%s)
age_seconds=$((now_epoch - latest_epoch))
maximum_age_seconds=$((max_backup_age_hours * 3600))

if ((age_seconds < 0 || age_seconds > maximum_age_seconds)); then
  echo "Latest successful backup is outside the allowed ${max_backup_age_hours}-hour window: $latest_end_time." >&2
  exit 1
fi

age_hours=$((age_seconds / 3600))
echo "Cloud SQL backup gate passed for $instance_name: latest backup is ${age_hours}h old."
