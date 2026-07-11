#!/usr/bin/env bash

set -Eeuo pipefail

api_base_url=${1:-}
frontend_url=${2:-}
expected_environment=${3:-}

if [[ ! $api_base_url =~ ^https://[^[:space:]]+$ ]] || [[ ! $frontend_url =~ ^https://[^[:space:]]+$ ]]; then
  echo "E2E URLs must be explicit HTTPS URLs." >&2
  exit 1
fi

case "$expected_environment" in
  staging | production) ;;
  *)
    echo "E2E environment must be staging or production." >&2
    exit 1
    ;;
esac

if [[ -z ${E2E_USERNAME:-} ]] || [[ -z ${E2E_PASSWORD:-} ]]; then
  echo "E2E_USERNAME and E2E_PASSWORD must be configured for the deployment environment." >&2
  exit 1
fi

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

api_base_url=${api_base_url%/}
frontend_url=${frontend_url%/}

if [[ ${E2E_VALIDATE_ONLY:-false} == true ]]; then
  echo "Authenticated release E2E configuration is valid for $expected_environment."
  exit 0
fi

response_file=$(mktemp)
readonly response_file
access_token=
refresh_token=
session_closed=0

cleanup() {
  local exit_status=$?

  if [[ -n $access_token && -n $refresh_token && $session_closed -eq 0 ]]; then
    local cleanup_payload
    cleanup_payload=$(jq -cn --arg refresh_token "$refresh_token" '{refreshToken: $refresh_token}')
    curl --silent --show-error \
      --connect-timeout 5 --max-time 15 \
      --request POST \
      --header "Authorization: Bearer $access_token" \
      --header 'Content-Type: application/json' \
      --data "$cleanup_payload" \
      --output /dev/null \
      "$api_base_url/api/v1/auth/logout" || true
  fi

  rm -f "$response_file"
  exit "$exit_status"
}
trap cleanup EXIT

request() {
  local method=$1
  local url=$2
  local expected_status=$3
  shift 3

  local status
  status=$(curl --silent --show-error --location \
    --retry 4 --retry-all-errors --retry-delay 2 \
    --connect-timeout 10 --max-time 30 \
    --request "$method" \
    --output "$response_file" \
    --write-out '%{http_code}' \
    "$@" "$url")

  if [[ $status != "$expected_status" ]]; then
    echo "E2E request failed: $method $url returned $status; expected $expected_status." >&2
    return 1
  fi
}

assert_json() {
  local expression=$1
  local description=$2
  shift 2

  if ! jq -e "$@" "$expression" "$response_file" >/dev/null; then
    echo "E2E response validation failed: $description." >&2
    return 1
  fi
}

echo "Checking frontend runtime configuration."
request GET "$frontend_url/runtime-config.js" 200
grep -Fq "VITE_VIRTUALRIFT_ENVIRONMENT: \"$expected_environment\"" "$response_file" || {
  echo "Frontend runtime environment does not match $expected_environment." >&2
  exit 1
}
grep -Fq "VITE_API_BASE_URL: \"$api_base_url\"" "$response_file" || {
  echo "Frontend runtime API URL does not match the release API URL." >&2
  exit 1
}

echo "Checking unauthenticated API protection."
request GET "$api_base_url/api/v1/auth/me" 401

echo "Authenticating the synthetic release user."
login_payload=$(jq -cn \
  --arg email "$E2E_USERNAME" \
  --arg password "$E2E_PASSWORD" \
  '{email: $email, password: $password}')
request POST "$api_base_url/api/v1/auth/token" 200 \
  --header 'Content-Type: application/json' \
  --data "$login_payload"
assert_json '.accessToken | type == "string" and length > 20' "access token is missing"
assert_json '.refreshToken | type == "string" and length > 20' "refresh token is missing"

access_token=$(jq -r '.accessToken' "$response_file")
refresh_token=$(jq -r '.refreshToken' "$response_file")
if [[ -n ${GITHUB_ACTIONS:-} ]]; then
  printf '::add-mask::%s\n' "$access_token"
  printf '::add-mask::%s\n' "$refresh_token"
fi

auth_header="Authorization: Bearer $access_token"

echo "Checking authenticated identity through the gateway."
request GET "$api_base_url/api/v1/auth/me" 200 --header "$auth_header"
# shellcheck disable=SC2016 # jq variables are bound with --arg below.
assert_json '
  .email == ($email | ascii_downcase) and
  .status == "ACTIVE" and
  (.id | test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) and
  (.tenantId | test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) and
  (.roles | type == "array" and length > 0)
' "authenticated profile is inconsistent" --arg email "$E2E_USERNAME"
tenant_id=$(jq -r '.tenantId' "$response_file")
readonly tenant_id

echo "Checking tenant and quota services."
request GET "$api_base_url/api/v1/tenants/$tenant_id" 200 --header "$auth_header"
# shellcheck disable=SC2016 # jq variables are bound with --arg below.
assert_json '.id == $tenant_id and .status == "ACTIVE"' \
  "tenant identity or status is inconsistent" --arg tenant_id "$tenant_id"

request GET "$api_base_url/api/v1/tenants/$tenant_id/quota" 200 --header "$auth_header"
assert_json '
  (.maxScansPerDay | type == "number" and . >= 0) and
  (.maxConcurrentScans | type == "number" and . > 0) and
  (.maxScanTargets | type == "number" and . > 0) and
  (.reportRetentionDays | type == "number" and . > 0) and
  (.sastEnabled | type == "boolean")
' "tenant quota is invalid"

echo "Checking orchestrator and reports read paths."
request GET "$api_base_url/api/v1/scans" 200 --header "$auth_header"
assert_json 'type == "array"' "scan list is not an array"

request GET "$api_base_url/api/v1/reports" 200 --header "$auth_header"
assert_json 'type == "array"' "report list is not an array"

echo "Closing the synthetic session and checking token revocation."
logout_payload=$(jq -cn --arg refresh_token "$refresh_token" '{refreshToken: $refresh_token}')
request POST "$api_base_url/api/v1/auth/logout" 204 \
  --header "$auth_header" \
  --header 'Content-Type: application/json' \
  --data "$logout_payload"
session_closed=1
request GET "$api_base_url/api/v1/auth/me" 401 --header "$auth_header"

echo "Authenticated release E2E tests passed for $expected_environment."
