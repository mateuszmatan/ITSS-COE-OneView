#!/usr/bin/env bash
set -euo pipefail

ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"
PRODUCT_ID="${1:?usage: product-pipelines.sh PRODUCT_ID}"

curl -sS --fail-with-body "$ONEVIEW_URL/api/products/$PRODUCT_ID/pipelines" \
  | jq 'if type == "array" then [.[] | {service: .serviceName, buildTool, deployTarget,
         pipelines: [.pipelines[] | {id, type, enabled, entryPoint, jenkinsJob, key: (.activeKey.value // null)}]}]
         else . end'
