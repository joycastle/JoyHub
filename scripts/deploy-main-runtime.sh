#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf '%s\n' \
    'Usage: scripts/deploy-main-runtime.sh --host <host> --key-file <path> --known-hosts-file <path> --deploy-tag <tag> --commit-sha <sha> --run-url <url> [--user <user>] [--port <port>]'
}

ssh_host=""
ssh_user="joyhub-deploy"
ssh_port="22"
ssh_key_file=""
known_hosts_file=""
deploy_tag=""
commit_sha=""
run_url=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) ssh_host="$2"; shift 2 ;;
    --user) ssh_user="$2"; shift 2 ;;
    --port) ssh_port="$2"; shift 2 ;;
    --key-file) ssh_key_file="$2"; shift 2 ;;
    --known-hosts-file) known_hosts_file="$2"; shift 2 ;;
    --deploy-tag) deploy_tag="$2"; shift 2 ;;
    --commit-sha) commit_sha="$2"; shift 2 ;;
    --run-url) run_url="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unsupported argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

[[ -n "${ssh_host}" ]] || { echo "--host is required" >&2; exit 1; }
[[ -f "${ssh_key_file}" ]] || { echo "--key-file must point to a file" >&2; exit 1; }
[[ -f "${known_hosts_file}" ]] || { echo "--known-hosts-file must point to a file" >&2; exit 1; }
[[ "${ssh_port}" =~ ^[0-9]{1,5}$ ]] || { echo "Invalid SSH port" >&2; exit 1; }
[[ "${ssh_user}" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo "Invalid SSH user" >&2; exit 1; }
[[ "${deploy_tag}" =~ ^main-[0-9a-f]{40}$ ]] || { echo "Invalid deploy tag" >&2; exit 1; }
[[ "${commit_sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit SHA" >&2; exit 1; }
[[ "${run_url}" =~ ^https://github\.com/joycastle/JoyHub/actions/runs/[0-9]+$ ]] \
  || { echo "Invalid run URL" >&2; exit 1; }
[[ -n "${GHCR_TOKEN:-}" ]] || { echo "GHCR_TOKEN is required" >&2; exit 1; }
[[ "${GHCR_USER:-}" =~ ^[A-Za-z0-9][A-Za-z0-9-]*$ ]] || { echo "GHCR_USER is invalid" >&2; exit 1; }

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release_files=(
  compose.release.yml
  compose.light.release.yml
  compose.api.release.yml
  compose.deployment.release.yml
)

for release_file in "${release_files[@]}"; do
  [[ -f "${repo_root}/${release_file}" ]] || { echo "Missing ${release_file}" >&2; exit 1; }
done

ssh_opts=(
  -i "${ssh_key_file}"
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="${known_hosts_file}"
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=3
  -o ConnectTimeout=10
  -p "${ssh_port}"
)
scp_opts=(
  -i "${ssh_key_file}"
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="${known_hosts_file}"
  -P "${ssh_port}"
)

remote="${ssh_user}@${ssh_host}"
remote_dir="$(ssh "${ssh_opts[@]}" "${remote}" 'mktemp -d /tmp/joyhub-release.XXXXXX')"
[[ "${remote_dir}" =~ ^/tmp/joyhub-release\.[A-Za-z0-9]+$ ]] \
  || { echo "Remote returned an unsafe temporary directory" >&2; exit 1; }

cleanup() {
  ssh "${ssh_opts[@]}" "${remote}" "rm -rf -- '${remote_dir}'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for release_file in "${release_files[@]}"; do
  scp "${scp_opts[@]}" "${repo_root}/${release_file}" "${remote}:${remote_dir}/${release_file}"
done

printf '%s\n' "${GHCR_TOKEN}" | ssh "${ssh_opts[@]}" "${remote}" \
  sudo /usr/local/sbin/joyhub-deploy \
    --artifact-dir "${remote_dir}" \
    --deploy-tag "${deploy_tag}" \
    --commit-sha "${commit_sha}" \
    --run-url "${run_url}" \
    --registry-user "${GHCR_USER}"
