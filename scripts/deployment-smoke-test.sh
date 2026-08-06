#!/usr/bin/env bash
set -euo pipefail

API_BASE="${1:-http://localhost:8080}"
STATIC_BASE="${2:-http://localhost:8090}"
RUNNER_BASE="${3:-http://localhost:8091}"
RUNNER_TOKEN="${JOYHUB_RUNNER_TOKEN:-local-deployment-token}"
SMOKE_ID="$(date +%s)"
SLUG="deploy-smoke-${SMOKE_ID}"
AUTOMATED_SLUG="deploy-auto-smoke-${SMOKE_ID}"
TMP_DIR="$(mktemp -d)"
ADMIN_COOKIES="$TMP_DIR/admin.cookies"
USER_COOKIES="$TMP_DIR/user.cookies"
ADMIN_HEADERS=(-H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIES" -c "$ADMIN_COOKIES")
USER_HEADERS=(-H "X-Mock-User-Id: local-user" -b "$USER_COOKIES" -c "$USER_COOKIES")

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

json_value() {
  local expression="$1"
  python3 -c 'import json,sys
value=json.load(sys.stdin)
for part in sys.argv[1].split("."):
    if part:
        value=value[int(part)] if isinstance(value,list) else value[part]
print(value if value is not None else "")' "$expression"
}

csrf_token() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$1" | tail -n 1
}

post_json() {
  local path="$1"
  local body="$2"
  curl --fail-with-body -sS -X POST "$API_BASE$path" \
    "${ADMIN_HEADERS[@]}" \
    -H "X-XSRF-TOKEN: $(csrf_token "$ADMIN_COOKIES")" \
    -H "Content-Type: application/json" \
    -d "$body"
}

python3 - "$TMP_DIR" <<'PY'
import pathlib, sys, zipfile
root = pathlib.Path(sys.argv[1])
for name, content in (("v1.zip", "version-one"), ("v2.zip", "version-two")):
    with zipfile.ZipFile(root / name, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("index.html", content)
with zipfile.ZipFile(root / "bad.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("not-index.html", "broken")
PY

curl --fail-with-body -sS "${ADMIN_HEADERS[@]}" \
  "$API_BASE/api/v1/auth/providers" >/dev/null

CREATE_RESPONSE="$(post_json "/api/v1/catalog/resources" "{
  \"slug\":\"$SLUG\",
  \"name\":\"Deployment smoke $SMOKE_ID\",
  \"summary\":\"Static deployment end-to-end smoke resource\",
  \"kind\":\"ONLINE_TOOL\",
  \"documentation\":\"Created by deployment smoke test\",
  \"maintenanceStatus\":\"ACTIVE\",
  \"visibilityScope\":\"COMPANY\",
  \"visibleDepartmentIds\":[],
  \"scenarios\":[],
  \"tags\":[],
  \"relatedResourceIds\":[],
  \"relatedSkillIds\":[],
  \"publish\":false
}")"
CATALOG_ID="$(printf '%s' "$CREATE_RESPONSE" | json_value data.id)"

upload_artifact() {
  local slug="$1"
  local archive="$2"
  curl --fail-with-body -sS -X POST "$API_BASE/api/v1/catalog/resources/$slug/artifact" \
    "${ADMIN_HEADERS[@]}" \
    -H "X-XSRF-TOKEN: $(csrf_token "$ADMIN_COOKIES")" \
    -F "file=@$archive;type=application/zip" >/dev/null
}

post_json "/api/v1/catalog/resources" "{
  \"slug\":\"$AUTOMATED_SLUG\",
  \"name\":\"Automated deployment smoke $SMOKE_ID\",
  \"summary\":\"Catalog one-click deployment smoke resource\",
  \"kind\":\"ONLINE_TOOL\",
  \"documentation\":\"Created by deployment smoke test\",
  \"maintenanceStatus\":\"ACTIVE\",
  \"visibilityScope\":\"COMPANY\",
  \"visibleDepartmentIds\":[],
  \"scenarios\":[],
  \"tags\":[],
  \"relatedResourceIds\":[],
  \"relatedSkillIds\":[],
  \"publish\":false
}" >/dev/null
upload_artifact "$AUTOMATED_SLUG" "$TMP_DIR/v1.zip"
post_json "/api/v1/catalog/resources/$AUTOMATED_SLUG/publish" '{"version":"v1"}' >/dev/null
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$AUTOMATED_SLUG/")" == "version-one" ]]

upload_artifact "$AUTOMATED_SLUG" "$TMP_DIR/v2.zip"
post_json "/api/v1/catalog/resources/$AUTOMATED_SLUG/publish" '{"version":"v2"}' >/dev/null
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$AUTOMATED_SLUG/")" == "version-two" ]]

post_json "/api/v1/catalog/resources/$AUTOMATED_SLUG/offline" '{}' >/dev/null
AUTOMATED_OFFLINE_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' "$STATIC_BASE/apps/$AUTOMATED_SLUG/")"
[[ "$AUTOMATED_OFFLINE_STATUS" == "404" ]]

upload_artifact "$SLUG" "$TMP_DIR/v1.zip"
APPLICATION_RESPONSE="$(post_json "/api/v1/deployable-applications" \
  "{\"catalogResourceId\":$CATALOG_ID,\"deploymentMode\":\"STATIC\"}")"
APPLICATION_ID="$(printf '%s' "$APPLICATION_RESPONSE" | json_value data.id)"

V1_RESPONSE="$(post_json "/api/v1/deployable-applications/$APPLICATION_ID/releases" '{"version":"v1"}')"
V1_RELEASE_ID="$(printf '%s' "$V1_RESPONSE" | python3 -c 'import json,sys
for release in json.load(sys.stdin)["data"]["releases"]:
    if release["version"] == "v1": print(release["id"])')"
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$SLUG/")" == "version-one" ]]

upload_artifact "$SLUG" "$TMP_DIR/v2.zip"
V2_RESPONSE="$(post_json "/api/v1/deployable-applications/$APPLICATION_ID/releases" '{"version":"v2"}')"
V2_RELEASE_ID="$(printf '%s' "$V2_RESPONSE" | python3 -c 'import json,sys
for release in json.load(sys.stdin)["data"]["releases"]:
    if release["version"] == "v2": print(release["id"])')"
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$SLUG/")" == "version-two" ]]

post_json "/api/v1/deployable-applications/$APPLICATION_ID/rollback" \
  "{\"targetReleaseId\":$V1_RELEASE_ID}" >/dev/null
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$SLUG/")" == "version-one" ]]

upload_artifact "$SLUG" "$TMP_DIR/bad.zip"
BAD_RESPONSE="$(post_json "/api/v1/deployable-applications/$APPLICATION_ID/releases" '{"version":"bad-v3"}')"
CURRENT_AFTER_BAD="$(printf '%s' "$BAD_RESPONSE" | json_value data.currentReleaseId)"
[[ "$CURRENT_AFTER_BAD" == "$V1_RELEASE_ID" ]]
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$SLUG/")" == "version-one" ]]

post_json "/api/v1/deployable-applications/$APPLICATION_ID/offline" '{}' >/dev/null
OFFLINE_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' "$STATIC_BASE/apps/$SLUG/")"
[[ "$OFFLINE_STATUS" == "404" ]]

post_json "/api/v1/deployable-applications/$APPLICATION_ID/restore" \
  "{\"targetReleaseId\":$V2_RELEASE_ID}" >/dev/null
[[ "$(curl --fail-with-body -sS "$STATIC_BASE/apps/$SLUG/")" == "version-two" ]]

UNAUTHORIZED_RUNNER_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' "$RUNNER_BASE/internal/v1/static/$SLUG/state")"
[[ "$UNAUTHORIZED_RUNNER_STATUS" == "401" ]]

curl --fail-with-body -sS "${USER_HEADERS[@]}" \
  "$API_BASE/api/v1/auth/providers" >/dev/null
FORBIDDEN_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' \
  "${USER_HEADERS[@]}" "$API_BASE/api/v1/deployable-applications/$APPLICATION_ID")"
[[ "$FORBIDDEN_STATUS" == "403" ]]

if [[ "${DEPLOYMENT_RESTART_RUNNER:-false}" == "true" ]]; then
  COMPOSE_PROJECT="${DEPLOYMENT_COMPOSE_PROJECT:-skillhub-staging}"
  docker compose -p "$COMPOSE_PROJECT" -f docker-compose.yml -f docker-compose.staging.yml \
    --profile deployment restart deployment-runner >/dev/null
  for _ in $(seq 1 30); do
    if curl -sf "$RUNNER_BASE/actuator/health" >/dev/null; then break; fi
    sleep 1
  done
fi

STATE_RESPONSE="$(curl --fail-with-body -sS \
  -H "Authorization: Bearer $RUNNER_TOKEN" "$RUNNER_BASE/internal/v1/static/$SLUG/state")"
[[ "$(printf '%s' "$STATE_RESPONSE" | json_value currentReleaseId)" == "$V2_RELEASE_ID" ]]

echo "PASS: automated and administrative deployment smoke completed for $AUTOMATED_SLUG and $SLUG"
