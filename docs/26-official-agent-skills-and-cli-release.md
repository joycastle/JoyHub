# Official Agent Skills and JoyHub CLI Release

This guide covers the two checked-in official agent skills and the release controls for
`@toolnets/joyhub-cli`. The CLI source and API contract are version `0.3.0`; npm publication is
pending publisher authorization. The currently published CLI pin used by the sharing skill is `0.2.0`.

## Official skills

- `official-agent-skills/find-skills-joyhub/SKILL.md` discovers every resource type through the
  connected JoyHub MCP. It reads details and uses revision-bound downloads and update checks;
  installation follows the user's resource and target selection. It has no npm CLI dependency.
- `official-agent-skills/share-skill-joyhub/SKILL.md` uses the published CLI `0.2.0` for validation
  and publication. Its network commands explicitly pass `--registry https://joyhub.toolnets.net`,
  parse JSON, and delegate authentication to the CLI.

Connect discovery clients to `https://joyhub.toolnets.net/mcp`. If MCP tools are unavailable, the
finder explains how to connect; it must not use the legacy Skill-only search endpoint to conclude
that no tool resource exists. Neither skill reads or copies credentials.

CLI `0.3.0` adds all-resource search through `/api/cli/v1/resources/search`; the legacy endpoint
remains Skill-only. Keep runtime skill dependencies on available distribution channels, and do not
advance the sharing skill's npm pin until npm actually serves the release.

### Install for Codex

Copy or link each complete skill directory into one of:

```text
<project>/.codex/skills/find-skills-joyhub/
<project>/.codex/skills/share-skill-joyhub/
~/.codex/skills/find-skills-joyhub/
~/.codex/skills/share-skill-joyhub/
```

Use project paths for repository-local availability and home paths for user-wide availability.

### Install for Claude Code

Copy or link each complete skill directory into one of:

```text
<project>/.claude/skills/find-skills-joyhub/
<project>/.claude/skills/share-skill-joyhub/
~/.claude/skills/find-skills-joyhub/
~/.claude/skills/share-skill-joyhub/
```

Validate the checked-in skills before distribution:

```bash
make validate-official-agent-skills
```

## CLI/API contract gate

`scripts/contracts/joyhub-cli-api.json` is the reviewed compatibility manifest for agent commands,
the npm package pin, and CLI-facing endpoints. It avoids inferring an API contract from fragile
source-text matching.

The PR workflow runs for every pull request so required CLI checks are always reported. The
contract checker identifies changes to CLI clients and commands, CLI-facing controllers, Device
Flow, and route security policy. If one of those boundary files changes, the same PR must update
the manifest to record that the contract was reviewed:

```bash
make check-cli-api-contract
BASE_REF=origin/main make check-cli-api-contract
```

Update the manifest only after checking method, path, authentication, request, response, and command
compatibility. A manifest-only acknowledgement does not replace behavioral tests.

## npm release

Before opening a release PR, `scripts/publish-cli.sh` runs lint, typecheck, tests, build, and:

```bash
cd cli
npm pack --dry-run
```

The pull request workflow runs the same package dry-run on Linux, macOS, and Windows. The tag
workflow repeats it before publication.

### First public publication

npm Trusted Publishing cannot create a package for the first time. A package owner must first
publish `@toolnets/joyhub-cli` publicly from a trusted local machine using an npm account with 2FA:

```bash
cd cli
npm publish --access public
```

Confirm the package name, public access, contents, and ownership before publishing. This is a
manual, one-time bootstrap; do not add the resulting npm credential to GitHub.

### Configure Trusted Publishing

After the package exists, configure its npm Trusted Publisher for:

- GitHub organization/repository: this repository
- Workflow filename: `release-cli.yml`
- Optional npm environment: leave unset unless the workflow is updated to use the same environment

The release workflow grants `id-token: write` and publishes with:

```bash
npm publish --access public --provenance --registry https://registry.npmjs.org
```

It intentionally does not read `secrets.NPM_TOKEN` or write a persistent npm token. Keep the
workflow filename stable because npm binds trust to it.

Release archives and checksums are named `joyhub-cli-<version>.*`, and release notes use the
`joyhub` command.
