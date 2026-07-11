#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly SCRIPT_DIR
REPOSITORY_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)
readonly REPOSITORY_ROOT

test_root=$(mktemp -d)
readonly test_root
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/bin"

cat >"$test_root/bin/curl" <<'MOCK_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail

output_file=
method=GET
authorization=
url=

while (($#)); do
  case "$1" in
    --output)
      output_file=$2
      shift 2
      ;;
    --request)
      method=$2
      shift 2
      ;;
    --header)
      if [[ $2 == Authorization:* ]]; then
        authorization=$2
      fi
      shift 2
      ;;
    --data | --write-out | --retry | --retry-delay | --connect-timeout | --max-time)
      shift 2
      ;;
    --silent | --show-error | --location | --retry-all-errors)
      shift
      ;;
    *)
      url=$1
      shift
      ;;
  esac
done

status=200
body='{}'
case "$method $url" in
  "GET https://app.example.com/runtime-config.js")
    body='window.__VIRTUALRIFT_CONFIG__ = {
  VITE_VIRTUALRIFT_ENVIRONMENT: "staging",
  VITE_API_BASE_URL: "https://api.example.com"
};'
    ;;
  "POST https://api.example.com/api/v1/auth/token")
    body='{"accessToken":"release-access-token-with-enough-length","refreshToken":"release-refresh-token-with-enough-length"}'
    ;;
  "GET https://api.example.com/api/v1/auth/me")
    if [[ -z $authorization || -f ${MOCK_STATE_DIR:?}/revoked ]]; then
      status=401
      body='{"status":401}'
    else
      body='{"id":"11111111-1111-1111-1111-111111111111","email":"release@example.com","tenantId":"22222222-2222-2222-2222-222222222222","status":"ACTIVE","roles":["READER"]}'
    fi
    ;;
  "GET https://api.example.com/api/v1/tenants/22222222-2222-2222-2222-222222222222")
    body='{"id":"22222222-2222-2222-2222-222222222222","status":"ACTIVE"}'
    ;;
  "GET https://api.example.com/api/v1/tenants/22222222-2222-2222-2222-222222222222/quota")
    body='{"maxScansPerDay":10,"maxConcurrentScans":1,"maxScanTargets":3,"reportRetentionDays":30,"sastEnabled":true}'
    ;;
  "GET https://api.example.com/api/v1/scans")
    if [[ -n ${MOCK_FAIL_SCANS:-} ]]; then
      status=503
      body='{"status":503}'
    else
      body='[]'
    fi
    ;;
  "GET https://api.example.com/api/v1/reports")
    body='[]'
    ;;
  "POST https://api.example.com/api/v1/auth/logout")
    status=204
    body=''
    touch "${MOCK_STATE_DIR:?}/revoked"
    ;;
  *)
    status=404
    body='{"status":404}'
    ;;
esac

printf '%s' "$body" >"$output_file"
printf '%s' "$status"
MOCK_CURL
chmod +x "$test_root/bin/curl"

cat >"$test_root/bin/gh" <<'MOCK_GH'
#!/usr/bin/env bash
set -Eeuo pipefail

if [[ -n ${MOCK_GH_LOG:-} ]]; then
  printf '%s\n' "$*" >>"$MOCK_GH_LOG"
fi

if [[ $* == *'/deployments/'*'/statuses'* && $* == *'--method POST'* ]]; then
  printf '{"id":99}'
elif [[ $* == *'/deployments/'*'/statuses'* ]]; then
  if [[ $* == *'/deployments/41/statuses'* ]]; then
    printf '[{"id":1,"state":"success","created_at":"2026-07-09T12:00:00Z"}]'
  elif [[ -n ${MOCK_STAGING_SUCCESS:-} ]]; then
    printf '[{"id":2,"state":"success","created_at":"2026-07-10T12:00:00Z"}]'
  else
    printf '[{"id":2,"state":"failure","created_at":"2026-07-10T12:00:00Z"}]'
  fi
elif [[ $* == *'--method POST'* ]]; then
  printf '{"id":42}'
else
  printf '[{"id":42,"created_at":"2026-07-10T11:00:00Z"},{"id":41,"created_at":"2026-07-09T11:00:00Z"}]'
fi
MOCK_GH
chmod +x "$test_root/bin/gh"

run_e2e() {
  rm -f "$test_root/revoked"
  PATH="$test_root/bin:$PATH" \
    MOCK_STATE_DIR="$test_root" \
    E2E_USERNAME=release@example.com \
    E2E_PASSWORD='synthetic-password' \
    "$REPOSITORY_ROOT/infra/scripts/e2e-test-release.sh" \
      https://api.example.com https://app.example.com staging
}

run_e2e >/dev/null

PATH="$test_root/bin:$PATH" \
  E2E_VALIDATE_ONLY=true \
  E2E_USERNAME=release@example.com \
  E2E_PASSWORD='synthetic-password' \
  "$REPOSITORY_ROOT/infra/scripts/e2e-test-release.sh" \
    https://api.example.com https://app.example.com production >/dev/null

rm -f "$test_root/revoked"
if PATH="$test_root/bin:$PATH" \
  MOCK_STATE_DIR="$test_root" \
  MOCK_FAIL_SCANS=1 \
  E2E_USERNAME=release@example.com \
  E2E_PASSWORD='synthetic-password' \
  "$REPOSITORY_ROOT/infra/scripts/e2e-test-release.sh" \
    https://api.example.com https://app.example.com staging >/dev/null 2>&1; then
  echo "E2E gate should fail when a critical endpoint is unavailable." >&2
  exit 1
fi
if [[ ! -f $test_root/revoked ]]; then
  echo "A failed E2E gate should clean up its synthetic session." >&2
  exit 1
fi

sha=0123456789abcdef0123456789abcdef01234567
PATH="$test_root/bin:$PATH" \
  MOCK_GH_LOG="$test_root/gh.log" \
  GH_TOKEN=test-token \
  GITHUB_REPOSITORY=example/virtualrift \
  "$REPOSITORY_ROOT/infra/scripts/record-staging-promotion.sh" "$sha" >/dev/null
if [[ $(grep -c -- '--method POST' "$test_root/gh.log") -ne 2 ]]; then
  echo "Staging qualification should create a deployment and a success status." >&2
  exit 1
fi

PATH="$test_root/bin:$PATH" \
  MOCK_STAGING_SUCCESS=1 \
  GH_TOKEN=test-token \
  GITHUB_REPOSITORY=example/virtualrift \
  "$REPOSITORY_ROOT/infra/scripts/verify-staging-promotion.sh" "$sha" >/dev/null

if PATH="$test_root/bin:$PATH" \
  GH_TOKEN=test-token \
  GITHUB_REPOSITORY=example/virtualrift \
  "$REPOSITORY_ROOT/infra/scripts/verify-staging-promotion.sh" "$sha" >/dev/null 2>&1; then
  echo "Production promotion should fail without a successful staging deployment." >&2
  exit 1
fi

echo "Release gate script tests passed."
