#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly SCRIPT_DIR

# shellcheck source=release-common.sh
source "$SCRIPT_DIR/release-common.sh"

environment=${1:-}
requested_revision=${2:-}

validate_environment "$environment"

require_command helm
require_command jq
require_command kubectl

namespace=$(resolve_namespace "$environment")
release_name=$(resolve_release_name)

history_json=$(helm history "$release_name" --namespace "$namespace" --output json)
current_revision=$(jq -r '[.[] | select(.status == "deployed")] | sort_by(.revision | tonumber) | .[-1].revision // empty' \
  <<<"$history_json")

if [[ -z $current_revision ]]; then
  echo "No deployed revision found for $release_name in $namespace." >&2
  exit 1
fi

if [[ -n $requested_revision ]]; then
  if [[ ! $requested_revision =~ ^[1-9][0-9]*$ ]]; then
    echo "Rollback revision must be a positive integer." >&2
    exit 1
  fi

  target_revision=$requested_revision
else
  target_revision=$(jq -r --arg current "$current_revision" '
    [
      .[]
      | select(.status == "superseded")
      | select((.revision | tonumber) < ($current | tonumber))
    ]
    | sort_by(.revision | tonumber)
    | .[-1].revision // empty
  ' <<<"$history_json")
fi

if [[ -z $target_revision ]]; then
  echo "No previous successful revision is available for rollback." >&2
  exit 1
fi

target_status=$(jq -r --arg target "$target_revision" '
  [.[] | select((.revision | tostring) == $target)] | .[-1].status // empty
' <<<"$history_json")

case "$target_status" in
  deployed | superseded) ;;
  *)
    echo "Revision $target_revision is not a successful Helm revision." >&2
    exit 1
    ;;
esac

if [[ $target_revision == "$current_revision" ]]; then
  echo "Revision $target_revision is already deployed." >&2
  exit 1
fi

helm rollback "$release_name" "$target_revision" \
  --namespace "$namespace" \
  --cleanup-on-fail \
  --wait \
  --timeout "${DEPLOY_TIMEOUT:-15m}"

wait_for_release_rollout "$release_name" "$namespace"

write_github_output rollback_revision "$target_revision"
write_github_output namespace "$namespace"
write_github_output release_name "$release_name"

echo "Rolled back $release_name in $namespace from revision $current_revision to $target_revision."
