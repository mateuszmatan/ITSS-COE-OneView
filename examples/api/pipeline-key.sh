#!/usr/bin/env bash
set -euo pipefail

# Exit status: 0 the key is printed, 1 the pipeline has no active key, 2 the service has no pipeline of the type,
# 3 no product has the code, 4 OneView did not answer or refused the request, 64 wrong arguments.
ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
ONEVIEW_URL="${ONEVIEW_URL%"${ONEVIEW_URL##*[!/]}"}"

usage() {
  echo "usage: pipeline-key.sh PRODUCT_CODE SERVICE [FULL|SECURITY|EXTENDED|SAST|NEXUS_IQ]" >&2
  exit 64
}
PRODUCT_CODE="${1:-}"
SERVICE="${2:-}"
TYPE="${3:-FULL}"
if [ -z "$PRODUCT_CODE" ] || [ -z "$SERVICE" ]; then
  usage
fi
case "$TYPE" in
  FULL|SECURITY|EXTENDED|SAST|NEXUS_IQ) ;;
  *) usage ;;
esac

ask() {
  local answer
  answer=$(curl -sS --fail-with-body "$@") || { [ -z "$answer" ] || printf '%s\n' "$answer" >&2; exit 4; }
  printf '%s\n' "$answer"
}

PRODUCT_ID=$(ask -G "$ONEVIEW_URL/api/products" --data-urlencode "search=$PRODUCT_CODE" \
  | jq --arg code "$PRODUCT_CODE" '[.[] | select(.code == $code) | .id] | first // empty')
if [ -z "$PRODUCT_ID" ]; then
  echo "No product with the code $PRODUCT_CODE" >&2
  exit 3
fi

PIPELINE=$(ask "$ONEVIEW_URL/api/products/$PRODUCT_ID/pipelines" \
  | jq -c --arg service "$SERVICE" --arg type "$TYPE" \
      '[.[] | select(.serviceName == $service) | .pipelines[] | select(.type == $type)] | first // empty')
if [ -z "$PIPELINE" ]; then
  echo "No $TYPE pipeline for $SERVICE of $PRODUCT_CODE" >&2
  exit 2
fi

KEY=$(jq -r '.activeKey.value // empty' <<<"$PIPELINE")
if [ -z "$KEY" ]; then
  echo "No active $TYPE pipeline key for $SERVICE of $PRODUCT_CODE: issue one with rotate-key.sh $(jq .id <<<"$PIPELINE")" >&2
  exit 1
fi
echo "$KEY"
