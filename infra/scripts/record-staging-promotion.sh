#!/usr/bin/env bash

set -Eeuo pipefail

image_sha=${1:-}
gate_state=${2:-success}

if [[ ! $image_sha =~ ^[0-9a-f]{40}$ ]]; then
  echo "Staging gate SHA must be a full lowercase 40-character Git commit SHA." >&2
  exit 1
fi

case "$gate_state" in
  success | failure) ;;
  *)
    echo "Staging gate state must be success or failure." >&2
    exit 1
    ;;
esac

if [[ ! ${GITHUB_REPOSITORY:-} =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "GITHUB_REPOSITORY must identify the owner and repository." >&2
  exit 1
fi

if [[ -z ${GH_TOKEN:-} ]]; then
  echo "GH_TOKEN is required to record the staging gate." >&2
  exit 1
fi

for command_name in gh jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

deployment_payload=$(jq -cn --arg ref "$image_sha" '{
  ref: $ref,
  task: "release:gate",
  environment: "staging-release-gate",
  description: "VirtualRift authenticated staging release gate",
  auto_merge: false,
  required_contexts: []
}')

deployment=$(gh api --method POST \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2026-03-10' \
  "/repos/$GITHUB_REPOSITORY/deployments" \
  --input - <<<"$deployment_payload")
deployment_id=$(jq -r '.id // empty' <<<"$deployment")

if [[ ! $deployment_id =~ ^[0-9]+$ ]]; then
  echo "GitHub did not return a valid staging gate deployment id." >&2
  exit 1
fi

status_payload=$(jq -cn --arg state "$gate_state" '{
  state: $state,
  environment: "staging-release-gate",
  description: (if $state == "success" then
    "Authenticated staging E2E release gate passed"
  else
    "Staging release gate failed"
  end),
  auto_inactive: true
}')

gh api --method POST \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2026-03-10' \
  "/repos/$GITHUB_REPOSITORY/deployments/$deployment_id/statuses" \
  --input - <<<"$status_payload" >/dev/null

echo "Recorded staging promotion gate $gate_state for $image_sha as deployment $deployment_id."
