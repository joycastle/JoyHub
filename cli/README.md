# JoyHub CLI

JoyHub CLI is the official command-line tool for JoyHub, designed for searching, installing, managing, and publishing Agent skill packages.

## 📦 Installation

```bash
# Install globally via npm
npm install -g @toolnets/joyhub-cli

# Or run directly with npx
npx @toolnets/joyhub-cli@latest version

# Or install globally via Bun
bun add -g @toolnets/joyhub-cli
```

## 🚀 Quick Start

```bash
# Ensure a valid login (opens Device Flow when needed)
joyhub auth ensure

# Search skills
joyhub search pdf

# Install skill to Agent directory
joyhub install pdf-parser --agent codex

# List installed skills
joyhub list

# Publish skill
joyhub publish ./my-skill --namespace myspace
```

## 🌐 Registry Configuration

The active registry is resolved in the following priority order:

1. `--registry <url>` command-line argument
2. `JOYHUB_REGISTRY` environment variable
3. `registry` in `~/.joyhub/config.json`
4. Default value `https://joyhub.toolnets.net`

```bash
# Temporarily use another registry
joyhub search pdf --registry https://joyhub.example.com

# Set via environment variable (Linux/macOS)
export JOYHUB_REGISTRY=https://joyhub.example.com
```

**Windows PowerShell:**

```powershell
$env:JOYHUB_REGISTRY="https://joyhub.example.com"
```

**Windows CMD:**

```cmd
set JOYHUB_REGISTRY=https://joyhub.example.com
```

## 🔐 Authentication

Token resolution priority:

1. `--token <token>` command-line argument
2. `JOYHUB_TOKEN` environment variable
3. Token stored in `~/.joyhub/credentials.json` (per registry)

### Login

```bash
# Browser-based Device Flow
joyhub auth ensure

# Machine-readable result for agents
joyhub auth ensure --json

# Login with API token
joyhub login --token sk_xxx

# Login to specific registry
joyhub login --token sk_xxx --registry https://joyhub.example.com
```

`login` validates the token, stores it in `~/.joyhub/credentials.json`, and writes the registry to `~/.joyhub/config.json`.
When no token is supplied, `login` uses the same Device Flow as `auth ensure`.

### Check Current Identity

```bash
joyhub whoami

# Check specific registry
joyhub whoami --registry https://joyhub.example.com

# Temporarily use different token
joyhub whoami --token sk_other
```

### Logout

```bash
joyhub logout

# Logout from specific registry
joyhub logout --registry https://joyhub.example.com
```

Logout only removes the token for the specified registry, preserving registry configuration and installation records.

## 🔍 Search

Search requires authentication. Agents should run `joyhub auth ensure --json` first.

```bash
joyhub search --query "pdf parser" --limit 10 --json
```

## Publishable Namespaces

```bash
joyhub namespaces --publishable --json
```

```bash
# Keyword search
joyhub search pdf

# Search with a one-off token
joyhub search pdf --token sk_xxx

# List all skills (empty query)
joyhub search "" --limit 50

# JSON output
joyhub search pdf --json
```

Output format: `namespace/slug  version  summary`

## 📥 Install Skills

The install coordinate accepts a bare slug or any of the equivalent namespace
forms below:

| Coordinate | Resolved namespace | Resolved slug |
|------------|--------------------|---------------|
| `my-skill` | `global` | `my-skill` |
| `team/my-skill` | `team` | `my-skill` |
| `@team/my-skill` | `team` | `my-skill` |
| `team--my-skill` | `team` | `my-skill` |

For a bare slug, `--namespace team` selects a non-global namespace. A
namespaced coordinate may be combined with the same `--namespace` value, but a
conflicting value is rejected instead of silently overriding the coordinate.

```bash
# Install to auto-detected Agent directory
joyhub install pdf-parser

# Equivalent namespaced coordinates
joyhub install team/my-skill
joyhub install @team/my-skill
joyhub install team--my-skill

# Choose install scope explicitly
joyhub install pdf-parser --scope user
joyhub install pdf-parser --scope project --agent codex

# Specify namespace for a bare slug (default: global)
joyhub install pdf-parser --namespace myspace

# Specify version
joyhub install pdf-parser --version 1.2.0

# Install to specific Agent
joyhub install pdf-parser --agent codex

# Install to multiple Agents
joyhub install pdf-parser --agent codex --agent claude-code

# Install to custom directory
joyhub install pdf-parser --dir ~/.claude/skills

# Force overwrite existing installation
joyhub install pdf-parser --force
```

### Install Target Resolution

The CLI determines the installation location using the following logic:

1. If `--dir` is specified: Install to that directory, agent marked as `custom`. `--dir` is mutually exclusive with `--scope` and `--agent`.
2. If `--scope user|project` is specified: Limit detection to the chosen scope.
   - With `--agent <profile>`: Install to that profile's user or project skills directory directly.
   - Without `--agent`: Detect existing skills directories within the chosen scope only. In interactive user scope, the `generic` target (`<home>/.agents/skills/`) is always also offered and can be selected alone or together with detected targets.
   - No detected directory in the chosen scope → Fallback to `<home>/.agents/skills/` for `--scope user` or `<cwd>/.agents/skills/` for `--scope project`.
3. If `--agent` is specified (no `--scope`): Install to the corresponding Agent's skills directory (existing behaviour, unchanged).
4. If none of the above is specified:
   - **Interactive mode** (stdin and stdout are both TTY, no `--json`): Prompt for `user` or `project` scope first, then continue per the `--scope` rule above.
   - **Non-interactive mode**: Auto-scan current directory to detect existing Agent config directories. 1 Agent detected → install directly; multiple → error; none detected → fallback to `<cwd>/.agents/skills/`.

> `--dir` cannot be combined with `--scope` or `--agent`.

### Install Paths

Each Agent has both project-level and user-level skills directories. Use `--scope user|project` to control which one is used.

| Agent | Project-level Path | User-level Path |
|-------|-------------------|-----------------|
| `claude-code` | `<project>/.claude/skills/` | `~/.claude/skills/` |
| `codex` | `<project>/.codex/skills/` | `~/.codex/skills/` |
| `cursor` | `<project>/.cursor/skills/` | `~/.cursor/skills/` |
| `github-copilot` | `<project>/.github-copilot/skills/` | `~/.github-copilot/skills/` |
| `gemini-cli` | `<project>/.gemini/skills/` | `~/.gemini/skills/` |
| `windsurf` | `<project>/.windsurf/skills/` | `~/.windsurf/skills/` |
| `kiro-cli` | `<project>/.kiro/skills/` | `~/.kiro/skills/` |
| `roo` | `<project>/.roo/skills/` | `~/.roo/skills/` |
| `trae` | `<project>/.trae/skills/` | `~/.trae/skills/` |
| `trae-cn` | `<project>/.trae-cn/skills/` | `~/.trae-cn/skills/` |
| `openhands` | `<project>/.openhands/skills/` | `~/.openhands/skills/` |
| `openclaw` | `<project>/.openclaw/skills/` | `~/.openclaw/skills/` |
| `opencode` | `<project>/.opencode/skills/` | `~/.opencode/skills/` |
| `kilo` | `<project>/.kilo/skills/` | `~/.kilo/skills/` |
| _fallback_ | `<project>/.agents/skills/` | `~/.agents/skills/` |

For a custom path or an unsupported Agent directory, use `--dir` to specify the installation path. In interactive user scope, the `generic` target is offered alongside detected Agent targets. When `--scope user|project` finds no matching agent directory, the CLI falls back to the `_fallback_` row above.

### File Structure After Installation

```
.codex/skills/pdf-parser/
├── ...                          # Extracted skill package files
└── .joyhub/
    └── metadata.json            # Installation metadata
```

`metadata.json` example:

```json
{
  "registry": "https://joyhub.toolnets.net",
  "namespace": "global",
  "slug": "pdf-parser",
  "version": "1.0.0",
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
```

## 📋 Local Management

### List Installed Skills

```bash
# List all installed skills
joyhub list

# Filter by Agent
joyhub list --agent codex

# Filter by multiple Agents
joyhub list --agent codex --agent claude-code

# Filter by directory
joyhub list --dir ~/.codex/skills

# JSON output
joyhub list --json
```

### Remove Skills

```bash
# A bare slug removes matching local installations across namespaces
joyhub remove pdf-parser

# A namespaced coordinate removes only that namespace
joyhub remove myspace/pdf-parser
joyhub remove @myspace/pdf-parser
joyhub remove myspace--pdf-parser

# Equivalent precise local removal with an explicit namespace
joyhub remove pdf-parser --namespace myspace

# Remove only specific Agent's installation
joyhub remove pdf-parser --agent codex

# Remove all targets (skip interactive confirmation)
joyhub remove pdf-parser --all

# Remove remote skill (requires authentication, prompts for confirmation)
joyhub remove pdf-parser --remote --namespace myspace

# Skip remote deletion confirmation
joyhub remove pdf-parser --remote --hard --namespace myspace
```

> Parameter exclusivity rules:
> - `--all` cannot be used with `--agent`
> - `--remote` cannot be used with `--agent` or `--all`
> - Remote deletion in non-interactive environments requires `--hard`

### Rebuild Local Inventory

```bash
joyhub doctor
```

`doctor` performs the following operations:

1. Scans `<cwd>/.<agent>/skills/<slug>/.joyhub/metadata.json`
2. Groups by `registry + namespace + slug`
3. Backs up old `inventory.json` (if exists)
4. Writes new `inventory.json`

If the same skill has version conflicts across different targets, that skill will be skipped and reported.

## 🚢 Publishing

```bash
# Publish directory (auto-packaged as zip)
joyhub publish ./my-skill --namespace myspace

# Publish existing zip file
joyhub publish ./my-skill.zip --namespace myspace

# Specify visibility
joyhub publish ./my-skill --namespace myspace --visibility private
```

Visibility options:
- `public` (default) — Visible to everyone
- `namespace-only` — Visible to namespace members only
- `private` — Visible to yourself only

After successful publication, the skill detail page URL will be displayed.

## ⬆️ Self-Update

```bash
# Check for new version
joyhub update --check

# Execute update
joyhub update
```

Update mechanism:
- Installed via npm globally: Auto-executes `npm install -g @toolnets/joyhub-cli@latest`
- Installed via Bun globally: Auto-executes `bun add -g @toolnets/joyhub-cli@latest`
- Run via npx: Prompts manual update command
- Unknown installation method: Prompts manual update

## 🔧 Environment Variables

| Variable | Description | Priority |
|----------|-------------|----------|
| `JOYHUB_REGISTRY` | Default registry URL | Lower than `--registry` parameter |
| `JOYHUB_TOKEN` | API token | Lower than `--token` parameter, higher than stored token |

## 📂 Local File Structure

```
~/.joyhub/
├── config.json           # User configuration (registry, defaultAgent, etc.)
├── credentials.json      # API tokens (stored per registry, permissions 0600)
└── inventory.json        # Installed skills inventory
```

## 📖 Command Reference

| Command | Description |
|---------|-------------|
| `joyhub help [command]` | Display help information |
| `joyhub version [--json]` | Display CLI version |
| `joyhub login --token <token> [--registry <url>] [--json]` | Save token and registry configuration |
| `joyhub logout [--registry <url>] [--json]` | Remove token for specified registry |
| `joyhub whoami [--registry <url>] [--token <token>] [--json]` | Validate current token and display user information |
| `joyhub search <query> [--registry <url>] [--token <token>] [--limit <n>] [--json]` | Search published skills |
| `joyhub install <coordinate> [--scope <user\|project>] [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--registry <url>] [--token <token>] [--json]` | Install a skill |
| `joyhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]` | List installed skills |
| `joyhub remove <coordinate> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--registry <url>] [--token <token>] [--json]` | Remove a skill |
| `joyhub doctor [--json]` | Scan project directory and rebuild local inventory |
| `joyhub publish <path> [--namespace <slug>] [--visibility <v>] [--registry <url>] [--token <token>] [--json]` | Publish a skill |
| `joyhub update [--check] [--json]` | Check or execute CLI self-update |

## 🔒 Security Notes

- Tokens are stored only in user directory `~/.joyhub/credentials.json`
- On Linux/macOS, credential file permissions are automatically set to `0600`
- Tokens are never written to any project-local files
- Remote delete operations require explicit confirmation or `--hard` parameter
- `remove` command validates path safety to prevent deletion of non-skill directories

## 🐛 Troubleshooting

### Authentication Failure

```bash
# Verify token validity
joyhub whoami

# Re-login
joyhub login --token sk_xxx
```

For structured registry failures, the CLI prints the server's public `msg` and
`requestId`. HTTP 403 without a public message falls back to `access denied`;
it is not automatically described as a missing token scope. Include the
request ID when asking a registry operator to investigate.

### Network Error

```bash
# Check if registry is accessible
curl https://joyhub.toolnets.net/api/cli/v1/skills/search?q=test&limit=1

# Use alternative registry
joyhub search test --registry https://joyhub.example.com
```

### Installation Directory Conflict

```bash
# Use --force to overwrite
joyhub install pdf-parser --force

# Or remove first then install
joyhub remove pdf-parser
joyhub install pdf-parser
```

### Corrupted Inventory

```bash
# Rebuild inventory
joyhub doctor
```

## 📚 Documentation

- [JoyHub Homepage](https://joyhub.toolnets.net)
- [GitHub Repository](https://github.com/joycastle/JoyHub)
- [CLI Documentation](https://github.com/joycastle/JoyHub/blob/main/docs/joyhub/en/guide/cli.md)
- [Issue Tracker](https://github.com/joycastle/JoyHub/issues)

## 📄 License

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.
