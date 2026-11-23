#!/usr/bin/env bash
set -euo pipefail

ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"
SEARCH="${1:-}"

curl -sS --fail-with-body -G "$ONEVIEW_URL/api/products" --data-urlencode "search=$SEARCH" | jq .
