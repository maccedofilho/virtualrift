#!/usr/bin/env bash

set -Eeuo pipefail

image_sha=${1:-}

if [[ ! $image_sha =~ ^[0-9a-f]{40}$ ]]; then
  echo "Promotion SHA must be a full lowercase 40-character Git commit SHA." >&2
  exit 1
fi

if [[ ! ${GITHUB_REPOSITORY:-} =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "GITHUB_REPOSITORY must identify the owner and repository." >&2
  exit 1
fi

if [[ -z ${GH_TOKEN:-} ]]; then
  echo "GH_TOKEN is required to verify staging deployments." >&2
  exit 1
fi

for command_name in gh jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

deployments=$(gh api --method GET \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2026-03-10' \
  "/repos/$GITHUB_REPOSITORY/deployments" \
  -f sha="$image_sha" \
  -f environment=staging-release-gate \
  -f task=release:gate \
  -f per_page=100)

deployment_id=$(jq -r 'max_by(.id) | .id // empty' <<<"$deployments")

if [[ ! $deployment_id =~ ^[0-9]+$ ]]; then
  echo "No staging release gate found for $image_sha; production promotion is blocked." >&2
  exit 1
fi

statuses=$(gh api --method GET \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2026-03-10' \
  "/repos/$GITHUB_REPOSITORY/deployments/$deployment_id/statuses" \
  -f per_page=100)

latest_state=$(jq -r 'max_by(.id) | .state // empty' <<<"$statuses")
if [[ $latest_state == success ]]; then
  echo "Staging promotion gate passed for $image_sha using deployment $deployment_id."
  exit 0
fi

echo "Latest staging release gate for $image_sha is $latest_state; production promotion is blocked." >&2
exit 1
