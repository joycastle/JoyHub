#!/usr/bin/env bash
# Fixed read-only diagnostics. Never print environment arrays, keys, or relay bodies.
set -euo pipefail

diagnostic_stderr="$(mktemp)"
trap 'rm -f "$diagnostic_stderr"' EXIT
if docker ps >/dev/null 2>"$diagnostic_stderr"; then
  docker_cmd=(docker)
elif sudo -n docker ps >/dev/null 2>>"$diagnostic_stderr"; then
  docker_cmd=(sudo -n docker)
else
  python3 - "$diagnostic_stderr" <<'PYERR'
import sys
text=open(sys.argv[1]).read().lower()
for needle,code in [("permission denied","DOCKER_PERMISSION_DENIED"),("command not found","DOCKER_CLI_NOT_FOUND"),("password","SUDO_PASSWORD_REQUIRED"),("not allowed","SUDO_COMMAND_NOT_ALLOWED"),("cannot connect","DOCKER_DAEMON_UNAVAILABLE")]:
    if needle in text:print("diagnostic_reason="+code)
PYERR
  sudo -n -l 2>/dev/null | python3 -c '
import re,sys
text=sys.stdin.read()
for name in sorted(set(re.findall(r"/usr/local/(?:sbin|bin)/joyhub[-a-z]*",text))):print("allowed_helper="+name)
' || true
  if test -r /opt/joyhub/.env.release; then
    python3 - <<'PYENV'
from pathlib import Path
import re
values={}
for line in Path("/opt/joyhub/.env.release").read_text().splitlines():
    if line.startswith("JOYHUB_AI_") and "=" in line:
        k,v=line.split("=",1);values[k]=v.strip().strip("\"\'")
print("runtime_env_readable=true")
print("ai_key_configured="+str(bool(values.get("JOYHUB_AI_API_KEY", ""))).lower())
print("ai_enabled="+str(values.get("JOYHUB_AI_ENABLED", "").lower()=="true").lower())
model=values.get("JOYHUB_AI_MODEL", "")
print("configured_model="+(model if re.fullmatch(r"gpt-[a-zA-Z0-9.\-]+",model) else "unset-or-custom"))
PYENV
  else
    printf '%s\n' 'runtime_env_readable=false'
  fi
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
