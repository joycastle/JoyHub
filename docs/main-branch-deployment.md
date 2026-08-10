# Main Branch Production Deployment

JoyHub deploys every successful push to `main` to the ARM64 production host at
`13.229.10.38`. The GitHub Actions workflow builds one immutable image set for the
server, web UI, scanner, and deployment runner, then updates the Docker Compose runtime
under `/opt/joyhub`.

## GitHub configuration

The `production` environment requires these secrets:

- `PROD_SSH_HOST`
- `PROD_SSH_USER`
- `PROD_SSH_PORT`
- `PROD_SSH_KEY`
- `PROD_SSH_KNOWN_HOSTS`

The deploy job uses the short-lived workflow token to authenticate the remote Docker
daemon to GHCR. The token is sent through SSH standard input and is never written to the
repository or passed as a process argument.

## Remote host contract

- Runtime directory: `/opt/joyhub`
- Compose project: `joyhub`
- Deploy user: `joyhub-deploy`
- Root-owned command: `/usr/local/sbin/joyhub-deploy`
- Web/API ports: `18080` and `18081`

The deploy user may run only the validated deployment command through passwordless
`sudo`. The command accepts release files from a deploy-user-owned temporary directory,
backs up PostgreSQL and the active Compose configuration, deploys a single immutable
`main-<commit-sha>` image set, and restores the previous files when a deployment fails.

## Rollback

Automatic rollback runs when Compose or health validation fails. Each deployment keeps a
backup under `/opt/joyhub/backups/deploy-<timestamp>-<sha>`, including a compressed
PostgreSQL dump and the previous environment and Compose files. The active deployment is
recorded in `/opt/joyhub/deployment.txt`.
