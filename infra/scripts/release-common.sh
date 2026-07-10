#!/usr/bin/env bash

set -Eeuo pipefail

readonly RELEASE_COMPONENTS=(
  gateway
  auth
  tenant
  orchestrator
  reports
  web-scanner
  api-scanner
  network-scanner
  sast
  frontend
)

readonly DEFAULT_REQUIRED_SECRETS=(
  virtualrift-jwt
  virtualrift-shared-secrets
  virtualrift-auth-db
  virtualrift-auth-secrets
  virtualrift-tenant-db
  virtualrift-tenant-secrets
  virtualrift-orchestrator-db
  virtualrift-reports-db
)

require_command() {
  local command_name=$1

  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
}

validate_environment() {
  local environment=$1

  case "$environment" in
    staging | production) ;;
    *)
      echo "Unsupported deployment environment: $environment" >&2
      exit 1
      ;;
  esac
}

validate_image_sha() {
  local image_sha=$1

  if [[ ! $image_sha =~ ^[0-9a-f]{40}$ ]]; then
    echo "Image SHA must be a full lowercase 40-character Git commit SHA." >&2
    exit 1
  fi
}

resolve_namespace() {
  local environment=$1
  local namespace=${KUBERNETES_NAMESPACE:-virtualrift-$environment}

  if [[ ! $namespace =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
    echo "Invalid Kubernetes namespace: $namespace" >&2
    exit 1
  fi

  printf '%s' "$namespace"
}

resolve_release_name() {
  local release_name=${HELM_RELEASE_NAME:-virtualrift}

  if [[ ! $release_name =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
    echo "Invalid Helm release name: $release_name" >&2
    exit 1
  fi

  printf '%s' "$release_name"
}

resolve_values_file() {
  local repository_root=$1
  local environment=$2
  local values_file="$repository_root/infra/helm/virtualrift/values-$environment.yaml"

  if [[ ! -f $values_file ]]; then
    echo "Helm values file not found: $values_file" >&2
    exit 1
  fi

  printf '%s' "$values_file"
}

current_deployed_revision() {
  local release_name=$1
  local namespace=$2
  local history_json

  if ! history_json=$(helm history "$release_name" --namespace "$namespace" --output json 2>/dev/null); then
    return 0
  fi

  jq -r '[.[] | select(.status == "deployed")] | sort_by(.revision | tonumber) | .[-1].revision // empty' \
    <<<"$history_json"
}

ensure_namespace() {
  local namespace=$1

  if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
    kubectl create namespace "$namespace"
  fi
}

verify_required_secrets() {
  local namespace=$1
  local -a secrets
  local missing=0

  if [[ -n ${REQUIRED_KUBERNETES_SECRETS:-} ]]; then
    read -r -a secrets <<<"$REQUIRED_KUBERNETES_SECRETS"
  else
    secrets=("${DEFAULT_REQUIRED_SECRETS[@]}")
  fi

  for secret_name in "${secrets[@]}"; do
    if ! kubectl get secret "$secret_name" --namespace "$namespace" >/dev/null 2>&1; then
      echo "Missing required Kubernetes Secret in $namespace: $secret_name" >&2
      missing=1
    fi
  done

  if ((missing)); then
    exit 1
  fi
}

wait_for_release_rollout() {
  local release_name=$1
  local namespace=$2
  local timeout=${DEPLOY_TIMEOUT:-15m}

  kubectl rollout status deployment \
    --selector "app.kubernetes.io/instance=$release_name" \
    --namespace "$namespace" \
    --timeout "$timeout"
}

write_github_output() {
  local key=$1
  local value=$2

  if [[ -n ${GITHUB_OUTPUT:-} ]]; then
    printf '%s=%s\n' "$key" "$value" >>"$GITHUB_OUTPUT"
  fi
}
