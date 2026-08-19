# Official Agent Skills and JoyHub CLI Release

This guide covers the two checked-in official agent skills and the release controls for
`@joycastle/joyhub-cli`. The supported CLI compatibility pin is `0.2.0`; the executable is
`joyhub`.

## Official skills

- `official-agent-skills/find-skills/SKILL.md` searches first and installs only after the user
  chooses an exact coordinate and target.
- `official-agent-skills/share-skill/SKILL.md` validates with `--dry-run`, shows the result, and
  publishes only after a separate user confirmation.

Both skills invoke:

```bash
npx --yes --package=@joycastle/joyhub-cli@0.2.0 joyhub auth ensure --json
```

They must not read JoyHub credentials, request tokens, call the API directly, or silently switch to
`latest`. CLI stdout is parsed as JSON and authentication errors stop the workflow.

### Install for Codex

Copy or link each complete skill directory into one of:

```text
<project>/.codex/skills/find-skills/
<project>/.codex/skills/share-skill/
~/.codex/skills/find-skills/
~/.codex/skills/share-skill/
```

Use project paths for repository-local availability and home paths for user-wide availability.

### Install for Claude Code

Copy or link each complete skill directory into one of:

```text
<project>/.claude/skills/find-skills/
<project>/.claude/skills/share-skill/
~/.claude/skills/find-skills/
~/.claude/skills/share-skill/
```

Validate the checked-in skills before distribution:

```bash
make validate-official-agent-skills
```

## CLI/API contract gate

`scripts/contracts/joyhub-cli-api.json` is the reviewed compatibility manifest for agent commands,
the npm package pin, and CLI-facing endpoints. It avoids inferring an API contract from fragile
source-text matching.

The PR workflow watches CLI clients and commands, CLI-facing controllers, Device Flow, and route
security policy. If one of those boundary files changes, the same PR must update the manifest to
record that the contract was reviewed:

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
publish `@joycastle/joyhub-cli` publicly from a trusted local machine using an npm account with 2FA:

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
