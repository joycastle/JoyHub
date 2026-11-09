#!/usr/bin/env bash
# Fixed read-only diagnostics. Never print environment arrays, keys, or relay bodies.
set -euo pipefail

if docker ps >/dev/null 2>&1; then
  docker_cmd=(docker)
elif sudo -n docker ps >/dev/null 2>&1; then
  docker_cmd=(sudo -n docker)
else
  printf '%s\n' 'diagnostic_access=unavailable: existing SSH account cannot read Docker state'
  exit 3
fi

"${docker_cmd[@]}" inspect joyhub-server-1 --format '{{json .Config.Env}}' | python3 -c '
import json,re,sys
values=dict(item.split("=",1) for item in json.load(sys.stdin) if "=" in item)
print("ai_enabled=" + str(values.get("JOYHUB_AI_ENABLED", "unset") in ("true", "TRUE", "1")).lower())
print("ai_key_configured=" + str(bool(values.get("JOYHUB_AI_API_KEY", "").strip())).lower())
for key in ("JOYHUB_AI_MODEL", "JOYHUB_AI_TRANSLATION_MODEL", "JOYHUB_AI_FALLBACK_MODEL"):
    value=values.get(key, "")
    safe=value if re.fullmatch(r"gpt-[a-zA-Z0-9.\-]+|Luna", value) else ("unset" if not value else "custom-model-configured")
    print(key.lower()+"="+safe)
'

"${docker_cmd[@]}" exec -i joyhub-postgres-1 sh -c '
  export PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=10000 -c lock_timeout=2000"
  exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
' <<'SQL'
BEGIN TRANSACTION READ ONLY;
SELECT resource_type, resource_id, generation_status, generation_attempts,
       generation_error_code, generation_next_retry_at, category_code, category_source, generated_at
FROM resource_search_document
WHERE generation_status = 'FAILED' OR (resource_type = 'TOOL' AND resource_id = 33)
ORDER BY resource_type, resource_id;
COMMIT;
SQL

"${docker_cmd[@]}" logs --since 15m joyhub-server-1 2>&1 \
  | grep -E 'Search profile generation failed|Primary JoyHub AI model request failed|JoyHub AI assistant degraded' \
  | tail -20 || true
