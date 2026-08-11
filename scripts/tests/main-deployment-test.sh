#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-main.yml"
client="${repo_root}/scripts/deploy-main-runtime.sh"
remote="${repo_root}/scripts/joyhub-deploy-remote.sh"
deployment_compose="${repo_root}/compose.deployment.release.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

for required_file in "${workflow}" "${client}" "${remote}" "${deployment_compose}"; do
  [[ -f "${required_file}" ]] || fail "Missing ${required_file}"
done

bash -n "${client}"
bash -n "${remote}"

grep -Fq 'branches: [main]' "${workflow}" || fail "Deployment must trigger from main"
grep -Fq 'cancel-in-progress: false' "${workflow}" || fail "Production deployments must be serialized"
grep -Fq 'packages: write' "${workflow}" || fail "Deployment must publish packages"
grep -Fq 'environment: production' "${workflow}" || fail "Deployment must use the production environment"
grep -Fq 'platforms: linux/arm64' "${workflow}" || fail "Production images must target the ARM64 host"
grep -Fq 'persist-credentials: false' "${workflow}" || fail "Checkout credentials must not persist"
grep -Fq 'PROD_SSH_KNOWN_HOSTS' "${workflow}" || fail "Pinned SSH host keys are required"
grep -Fq 'joyhub-deployment-runner' "${workflow}" || fail "Runner image must deploy with the application"
grep -Fq 'make web-install-ci' "${workflow}" || fail "Frontend dependencies must be installed before checks"

grep -Fq 'StrictHostKeyChecking=yes' "${client}" || fail "SSH host verification must be strict"
! grep -Fq 'StrictHostKeyChecking=accept-new' "${client}" || fail "TOFU host verification is not allowed"
grep -Fq 'docker login ghcr.io' "${remote}" || fail "Remote host must authenticate to GHCR"
grep -Fq 'pg_dump' "${remote}" || fail "Deployment must back up PostgreSQL"
grep -Fq "trap 'rollback \$?' ERR" "${remote}" || fail "Deployment must roll back on failure"
grep -Fq '/actuator/health' "${remote}" || fail "Backend health check is required"
grep -Fq '/nginx-health' "${remote}" || fail "Frontend health check is required"

grep -Fq 'JOYHUB_DEPLOYMENT_RUNNER_IMAGE' "${deployment_compose}" \
  || fail "Deployment runner image must be configurable"
grep -Fq 'SKILLHUB_VERSION' "${deployment_compose}" \
  || fail "Deployment runner must use the release version"

echo "main-deployment-test passed"
