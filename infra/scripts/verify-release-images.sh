#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly SCRIPT_DIR

# shellcheck source=release-common.sh
source "$SCRIPT_DIR/release-common.sh"

image_sha=${1:-}
image_prefix=${IMAGE_PREFIX:-ghcr.io/maccedofilho/virtualrift}

validate_image_sha "$image_sha"
require_command docker

if [[ ! $image_prefix =~ ^ghcr\.io/[a-z0-9._/-]+$ ]]; then
  echo "Invalid GHCR image prefix: $image_prefix" >&2
  exit 1
fi

image_tag="sha-$image_sha"

for component in "${RELEASE_COMPONENTS[@]}"; do
  image="$image_prefix-$component:$image_tag"
  echo "Checking published manifest: $image"
  docker manifest inspect "$image" >/dev/null
done

echo "All release images are available for $image_tag."
