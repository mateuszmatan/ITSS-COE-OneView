#!/usr/bin/env bash
set -euo pipefail

ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"

curl -sS --fail-with-body "$ONEVIEW_URL/api/departments" | jq .
