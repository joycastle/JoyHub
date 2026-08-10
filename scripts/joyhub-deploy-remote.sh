#!/usr/bin/env bash

set -euo pipefail

runtime_dir="/opt/joyhub"
project_name="joyhub"
artifact_dir=""
deploy_tag=""
commit_sha=""
run_url=""
registry_user=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-dir) artifact_dir="$2"; shift 2 ;;
    --deploy-tag) deploy_tag="$2"; shift 2 ;;
    --commit-sha) commit_sha="$2"; shift 2 ;;
    --run-url) run_url="$2"; shift 2 ;;
    --registry-user) registry_user="$2"; shift 2 ;;
    *) echo "Unsupported argument: $1" >&2; exit 1 ;;
  esac
done

[[ "${artifact_dir}" =~ ^/tmp/joyhub-release\.[A-Za-z0-9]+$ ]] || { echo "Invalid artifact directory" >&2; exit 1; }
[[ -d "${artifact_dir}" && ! -L "${artifact_dir}" ]] || { echo "Artifact directory is unavailable" >&2; exit 1; }
[[ "${deploy_tag}" =~ ^main-[0-9a-f]{40}$ ]] || { echo "Invalid deploy tag" >&2; exit 1; }
[[ "${commit_sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit SHA" >&2; exit 1; }
[[ "${run_url}" =~ ^https://github\.com/joycastle/JoyHub/actions/runs/[0-9]+$ ]] || { echo "Invalid run URL" >&2; exit 1; }
[[ "${registry_user}" =~ ^[A-Za-z0-9][A-Za-z0-9-]*$ ]] || { echo "Invalid registry user" >&2; exit 1; }
[[ -f "${runtime_dir}/.env.release" ]] || { echo "Missing ${runtime_dir}/.env.release" >&2; exit 1; }

release_files=(
  compose.release.yml
  compose.light.release.yml
  compose.api.release.yml
  compose.deployment.release.yml
)
for release_file in "${release_files[@]}"; do
  [[ -f "${artifact_dir}/${release_file}" && ! -L "${artifact_dir}/${release_file}" ]] \
    || { echo "Missing trusted release file: ${release_file}" >&2; exit 1; }
done

artifact_owner="$(stat -c '%U' "${artifact_dir}")"
[[ "${artifact_owner}" == "${SUDO_USER:-}" ]] || { echo "Artifact directory owner mismatch" >&2; exit 1; }

IFS= read -r registry_token
[[ -n "${registry_token}" ]] || { echo "Registry token is required on stdin" >&2; exit 1; }
printf '%s\n' "${registry_token}" | docker login ghcr.io --username "${registry_user}" --password-stdin >/dev/null
unset registry_token

get_env_value() {
  local key="$1"
  local default_value="${2:-}"
  local value
  value="$(grep -E "^${key}=" "${runtime_dir}/.env.release" | tail -n 1 | cut -d= -f2- || true)"
  printf '%s' "${value:-${default_value}}"
}

set_env_value() {
  local key="$1"
  local value="$2"
  local target="${runtime_dir}/.env.release"
  local temporary="${runtime_dir}/.env.release.tmp"

  if grep -q "^${key}=" "${target}"; then
    sed "s|^${key}=.*|${key}=${value}|" "${target}" > "${temporary}"
  else
    cp "${target}" "${temporary}"
    printf '%s=%s\n' "${key}" "${value}" >> "${temporary}"
  fi
  chmod 600 "${temporary}"
  mv "${temporary}" "${target}"
}

compose_args=(
  docker compose
  -p "${project_name}"
  --env-file "${runtime_dir}/.env.release"
  -f "${runtime_dir}/compose.release.yml"
  -f "${runtime_dir}/compose.light.release.yml"
  -f "${runtime_dir}/compose.api.release.yml"
  -f "${runtime_dir}/compose.deployment.release.yml"
)

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="${runtime_dir}/backups/deploy-${timestamp}-${commit_sha:0:12}"
install -d -m 700 "${backup_dir}"
cp -p "${runtime_dir}/.env.release" "${backup_dir}/.env.release"
for release_file in "${release_files[@]}"; do
  if [[ -f "${runtime_dir}/${release_file}" ]]; then
    cp -p "${runtime_dir}/${release_file}" "${backup_dir}/${release_file}"
  fi
done

postgres_user="$(get_env_value POSTGRES_USER skillhub)"
postgres_db="$(get_env_value POSTGRES_DB skillhub)"
"${compose_args[@]}" exec -T postgres \
  pg_dump -U "${postgres_user}" -d "${postgres_db}" | gzip -9 > "${backup_dir}/postgres.sql.gz"

rollback() {
  local exit_code="$1"
  trap - ERR
  echo "Deployment failed; restoring files from ${backup_dir}" >&2
  cp -p "${backup_dir}/.env.release" "${runtime_dir}/.env.release"
  for release_file in "${release_files[@]}"; do
    if [[ -f "${backup_dir}/${release_file}" ]]; then
      cp -p "${backup_dir}/${release_file}" "${runtime_dir}/${release_file}"
    fi
  done
  "${compose_args[@]}" up -d --wait || true
  exit "${exit_code}"
}
trap 'rollback $?' ERR

for release_file in "${release_files[@]}"; do
  install -m 0644 "${artifact_dir}/${release_file}" "${runtime_dir}/${release_file}"
done

set_env_value SKILLHUB_VERSION "${deploy_tag}"
set_env_value SKILLHUB_SERVICE_VERSION "${commit_sha}"
set_env_value SKILLHUB_SERVER_IMAGE ghcr.io/joycastle/skillhub-server
set_env_value SKILLHUB_WEB_IMAGE ghcr.io/joycastle/skillhub-web
set_env_value SKILLHUB_SCANNER_IMAGE ghcr.io/joycastle/skillhub-scanner
set_env_value JOYHUB_DEPLOYMENT_RUNNER_IMAGE ghcr.io/joycastle/joyhub-deployment-runner

"${compose_args[@]}" config --quiet
"${compose_args[@]}" pull server web skill-scanner deployment-runner
"${compose_args[@]}" up -d --wait

api_port="$(get_env_value API_PORT 18081)"
web_port="$(get_env_value WEB_PORT 18080)"
curl --fail --silent --show-error --retry 12 --retry-delay 5 \
  "http://127.0.0.1:${api_port}/actuator/health" >/dev/null
curl --fail --silent --show-error --retry 12 --retry-delay 5 \
  "http://127.0.0.1:${web_port}/nginx-health" >/dev/null

public_url="$(get_env_value SKILLHUB_PUBLIC_BASE_URL)"
if [[ -n "${public_url}" ]]; then
  curl --fail --silent --show-error --location --retry 6 --retry-delay 5 \
    --output /dev/null "${public_url}"
fi

cat > "${runtime_dir}/deployment.txt" <<METADATA
deployed_at=${timestamp}
deploy_tag=${deploy_tag}
commit_sha=${commit_sha}
run_url=${run_url}
backup_dir=${backup_dir}
METADATA

trap - ERR
echo "JoyHub deployed successfully with ${deploy_tag}"
