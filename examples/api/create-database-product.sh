#!/usr/bin/env bash
set -euo pipefail

# The Library defaults must allow the databases of both products (dev-db.bbh.com, rd-db.bbh.com and qc-db.bbh.com)
# and name the Nexus repositories of the database packages (Admin, Library defaults, Liquibase database changes),
# or OneView refuses the JDBC URLs and the Full pipelines.
ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"
DEPARTMENT="${1:-Custody}"
FOLDER="$(dirname "$0")"

DEPARTMENT_ID=$(curl -sS --fail-with-body "$ONEVIEW_URL/api/departments" \
  | jq -e --arg name "$DEPARTMENT" '.[] | select(.name == $name) | .id')

# A product OneView refuses (409 when its code is taken, 400 with the fields) does not stop the other one.
REFUSED=0
for BODY in "$FOLDER/new-database-product.json" "$FOLDER/new-mixed-product.json"; do
  echo "Creating $(jq -r .code "$BODY") in $DEPARTMENT (department $DEPARTMENT_ID)"
  jq --argjson id "$DEPARTMENT_ID" '.departmentId = $id' "$BODY" \
    | curl -sS --fail-with-body -X POST "$ONEVIEW_URL/api/products?pipelineType=FULL" \
        -H 'Content-Type: application/json' --data-binary @- \
    | jq 'if .detail then . else {id, code, name, departmentId, version, services: [.services[] | {id, name}]} end' \
    || REFUSED=1
done
exit "$REFUSED"
