#!/bin/sh
set -eu

escape_javascript_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

runtime_config=/usr/share/nginx/html/runtime-config.js
temporary_config=$(mktemp /tmp/virtualrift-runtime-config.XXXXXX)

environment=$(escape_javascript_string "${VITE_VIRTUALRIFT_ENVIRONMENT:-local}")
api_base_url=$(escape_javascript_string "${VITE_API_BASE_URL:-}")
github_oauth_start_url=$(escape_javascript_string "${VITE_GITHUB_OAUTH_START_URL:-}")
google_oauth_start_url=$(escape_javascript_string "${VITE_GOOGLE_OAUTH_START_URL:-}")

printf 'window.__VIRTUALRIFT_CONFIG__ = {\n' > "$temporary_config"
printf '  VITE_VIRTUALRIFT_ENVIRONMENT: "%s",\n' "$environment" >> "$temporary_config"
printf '  VITE_API_BASE_URL: "%s",\n' "$api_base_url" >> "$temporary_config"
printf '  VITE_GITHUB_OAUTH_START_URL: "%s",\n' "$github_oauth_start_url" >> "$temporary_config"
printf '  VITE_GOOGLE_OAUTH_START_URL: "%s"\n' "$google_oauth_start_url" >> "$temporary_config"
printf '};\n' >> "$temporary_config"

mv "$temporary_config" "$runtime_config"

exec nginx -g 'daemon off;'
