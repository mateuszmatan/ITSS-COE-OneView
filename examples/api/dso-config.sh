#!/usr/bin/env bash
set -euo pipefail

ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"
KEY="${1:?usage: dso-config.sh PIPELINE_KEY}"

echo "== JSON, as the library reads it"
curl -sS --fail-with-body "$ONEVIEW_URL/api/dso/config/$KEY?format=json" | jq .

echo "== YAML, the devsecops_config.yaml layout"
curl -sS --fail-with-body "$ONEVIEW_URL/api/dso/config/$KEY"
