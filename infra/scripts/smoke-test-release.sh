#!/usr/bin/env bash

set -Eeuo pipefail

api_health_url=${1:-}
frontend_health_url=${2:-}

if [[ ! $api_health_url =~ ^https:// ]] || [[ ! $frontend_health_url =~ ^https:// ]]; then
  echo "Smoke test URLs must be explicit HTTPS URLs." >&2
  exit 1
fi

check_endpoint() {
  local name=$1
  local url=$2

  echo "Checking $name health endpoint."
  curl --fail \
    --silent \
    --show-error \
    --location \
    --retry 12 \
    --retry-all-errors \
    --retry-delay 5 \
    --connect-timeout 10 \
    --max-time 30 \
    "$url" >/dev/null
}

check_endpoint API "$api_health_url"
check_endpoint frontend "$frontend_health_url"

echo "Deployment smoke tests passed."
