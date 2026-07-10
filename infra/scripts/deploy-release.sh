#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly SCRIPT_DIR
REPOSITORY_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
readonly REPOSITORY_ROOT

# shellcheck source=release-common.sh
source "$SCRIPT_DIR/release-common.sh"

environment=${1:-}
image_sha=${2:-}

validate_environment "$environment"
validate_image_sha "$image_sha"

require_command helm
require_command jq
require_command kubectl

namespace=$(resolve_namespace "$environment")
release_name=$(resolve_release_name)
values_file=$(resolve_values_file "$REPOSITORY_ROOT" "$environment")
chart_path="$REPOSITORY_ROOT/infra/helm/virtualrift"
image_tag="sha-$image_sha"

ensure_namespace "$namespace"
verify_required_secrets "$namespace"

previous_revision=$(current_deployed_revision "$release_name" "$namespace")
write_github_output previous_revision "$previous_revision"
write_github_output image_tag "$image_tag"
write_github_output namespace "$namespace"
write_github_output release_name "$release_name"

declare -a image_overrides=()
for component in "${RELEASE_COMPONENTS[@]}"; do
  image_overrides+=(
    --set-string "components.$component.image.tag=$image_tag"
    --set-string "components.$component.image.pullPolicy=IfNotPresent"
  )
done

helm upgrade "$release_name" "$chart_path" \
  --install \
  --namespace "$namespace" \
  --values "$chart_path/values.yaml" \
  --values "$values_file" \
  --atomic \
  --cleanup-on-fail \
  --wait \
  --timeout "${DEPLOY_TIMEOUT:-15m}" \
  --history-max "${HELM_HISTORY_MAX:-10}" \
  --description "Deploy $image_tag to $environment" \
  "${image_overrides[@]}"

wait_for_release_rollout "$release_name" "$namespace"

echo "Deployed $image_tag to $environment as $release_name in namespace $namespace."
