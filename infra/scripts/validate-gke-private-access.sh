#!/usr/bin/env bash

set -Eeuo pipefail

project_id=${1:-}
cluster_name=${2:-}
location=${3:-}
membership_name=${4:-}

if [[ -z $project_id || -z $cluster_name || -z $location || -z $membership_name ]]; then
  echo "Usage: $0 <project-id> <cluster-name> <location> <fleet-membership-name>" >&2
  exit 1
fi

for command_name in gcloud jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

cluster_json=$(gcloud container clusters describe "$cluster_name" \
  --location "$location" --project "$project_id" --format=json)
if ! jq -e '
  .status == "RUNNING" and
  .privateClusterConfig.enablePrivateNodes == true and
  .privateClusterConfig.enablePrivateEndpoint == true
' <<<"$cluster_json" >/dev/null; then
  echo "GKE cluster is not running with private nodes and a private-only control-plane endpoint." >&2
  exit 1
fi

membership_json=$(gcloud container fleet memberships describe "$membership_name" \
  --location=global --project "$project_id" --format=json)
if ! jq -e '.state.code == "READY"' <<<"$membership_json" >/dev/null; then
  echo "GKE Fleet membership is not ready for Connect Gateway access." >&2
  exit 1
fi

echo "GKE private-access gate passed for $cluster_name through membership $membership_name."
