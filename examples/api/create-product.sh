#!/usr/bin/env bash
set -euo pipefail

ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"
DEPARTMENT="${1:-Custody}"
BODY="$(dirname "$0")/new-product.json"

DEPARTMENT_ID=$(curl -sS --fail-with-body "$ONEVIEW_URL/api/departments" \
  | jq -e --arg name "$DEPARTMENT" '.[] | select(.name == $name) | .id')

echo "Creating the product in $DEPARTMENT (department $DEPARTMENT_ID)"
jq --argjson id "$DEPARTMENT_ID" '.departmentId = $id' "$BODY" \
  | curl -sS --fail-with-body -X POST "$ONEVIEW_URL/api/products?pipelineType=FULL" \
      -H 'Content-Type: application/json' --data-binary @- \
  | jq 'if .detail then . else {id, code, name, departmentId, version, services: [.services[] | {id, name}]} end'
