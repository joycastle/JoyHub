# JoyHub CLI

JoyHub CLI 是 JoyHub 的官方命令行工具，用于搜索、安装、管理和发布 Agent 技能包。官方 Agent Skill `find-skills` 与 `share-skill` 通过同一 CLI 完成认证、搜索、安装和发布。

## 运行方式

推荐使用固定兼容版本 `0.2.0` 的 `npx` 按需运行，不要求全局安装：

```bash
npx --yes --package=@toolnets/joyhub-cli@0.2.0 joyhub version
```

需要 Node.js 和 npm。官方 Skill 必须固定 `@toolnets/joyhub-cli@0.2.0`，不要改用无约束的 `latest`。下文示例使用二进制名 `joyhub`；等价写法是在命令前加上 `npx --yes --package=@toolnets/joyhub-cli@0.2.0`。

## 快速开始

```bash
# 首次使用：浏览器授权（无需复制 Token）
joyhub auth ensure

# 搜索需要登录；结果为当前用户可见的技能
joyhub search pdf

# 安装技能到 Agent 目录
joyhub install pdf-parser --agent codex

# 查看已安装技能
joyhub list

# 发布技能
joyhub publish ./my-skill --namespace myspace
```

## Registry 配置

当前生效的 registry 按以下优先级解析：

1. `--registry <url>` 命令行参数
2. `JOYHUB_REGISTRY` 环境变量
3. 用户配置文件 `~/.joyhub/config.json` 中的 `registry` 字段
4. 默认值 `https://skill.xfyun.cn`

```bash
# 临时使用其他 registry
joyhub search pdf --registry https://skillhub.example.com

# 通过环境变量设置（Linux/macOS）
export JOYHUB_REGISTRY=https://skillhub.example.com
```

**Windows PowerShell:**

```powershell
$env:JOYHUB_REGISTRY="https://skillhub.example.com"
```

**Windows CMD:**

```cmd
set JOYHUB_REGISTRY=https://skillhub.example.com
```

## 认证

Token 按以下优先级解析：

1. `--token <token>` 命令行参数
2. `JOYHUB_TOKEN` 环境变量
3. `~/.joyhub/credentials.json` 中存储的 token（按 registry 区分）

### 登录

```bash
# 浏览器 Device Flow（推荐；Token 由 CLI 写入 ~/.joyhub/credentials.json）
joyhub auth ensure

# Agent / 官方 Skill 使用的机器可读结果
joyhub auth ensure --json

# CI 等场景仍可用 API token
joyhub login --token sk_xxx

# 指定 registry 登录
joyhub login --token sk_xxx --registry https://skillhub.example.com
```

`auth ensure` 会校验当前 registry 的本地凭证：有效则直接继续；缺失、过期或被撤销时打开完整授权地址。无法打开浏览器时会输出可点击授权地址和一次性验证码。`login --token` 会验证 token 有效性，然后将 token 存储到 `~/.joyhub/credentials.json`，同时将 registry 写入 `~/.joyhub/config.json`。未提供 `--token` 时，`login` 使用与 `auth ensure` 相同的 Device Flow。

### 查看当前身份

```bash
joyhub whoami

# 指定 registry 查看
joyhub whoami --registry https://skillhub.example.com

# 临时使用其他 token
joyhub whoami --token sk_other
```

### 登出

```bash
joyhub logout

# 登出指定 registry
joyhub logout --registry https://skillhub.example.com
```

登出只删除对应 registry 的 token，保留 registry 配置和安装记录。

## 搜索

搜索需要认证。未登录时服务端返回 `401`，请先运行 `joyhub auth ensure`。官方 Skill `find-skills` 在搜索前会调用 `joyhub auth ensure --json`。搜索结果只包含当前用户有权查看的技能，不会回退为匿名搜索。

```bash
# 关键词搜索
joyhub search pdf

# 列出所有技能（空字符串查询）
joyhub search "" --limit 50

# JSON 输出
joyhub search pdf --json
```

输出格式：`namespace/slug  version  summary`

## 安装技能

安装坐标支持裸 slug（默认解析到 `global`）和三种等价的显式 namespace
形式。显式坐标与 `--namespace` 同时出现时，两者必须一致。

```bash
# 安装到自动探测的 Agent 目录
joyhub install pdf-parser

# 等价的 namespace 坐标
joyhub install team/my-skill
joyhub install @team/my-skill
joyhub install team--my-skill

# 显式指定安装范围
joyhub install pdf-parser --scope user
joyhub install pdf-parser --scope project --agent codex

# 为裸 slug 指定 namespace
joyhub install pdf-parser --namespace myspace

# 指定版本
joyhub install pdf-parser --version 1.2.0

# 安装到指定 Agent
joyhub install pdf-parser --agent codex

# 安装到多个 Agent
joyhub install pdf-parser --agent codex --agent claude-code

# 安装到自定义目录
joyhub install pdf-parser --dir ~/.claude/skills

# 强制覆盖已存在的安装
joyhub install pdf-parser --force
```

### 安装目标解析

CLI 按以下逻辑确定安装位置：

1. 指定 `--dir`：安装到该目录，agent 标记为 `custom`。`--dir` 与 `--scope`、`--agent` 互斥。
2. 指定 `--scope user|project`：探测限定在该 scope 内。
   - 同时指定 `--agent <profile>`：直接安装到该 profile 对应 scope 的 skills 目录。
   - 未指定 `--agent`：只探测该 scope 下已存在的 skills 目录。在交互式 user scope 下，始终额外提供 `generic` 目标（`<home>/.agents/skills/`），可单独选择或与已探测目标同时选择。
   - 该 scope 下未探测到 → fallback：`--scope user` 回退到 `<home>/.agents/skills/`，`--scope project` 回退到 `<cwd>/.agents/skills/`。
3. 指定 `--agent`（无 `--scope`）：安装到对应 Agent 的 skills 目录（沿用现有行为，不变）。
4. 三者均未指定：
   - **交互模式**（stdin 和 stdout 都是 TTY 且未传 `--json`）：先交互式询问 user 还是 project scope，再按 `--scope` 规则继续。
   - **非交互模式**：自动扫描当前目录探测已存在的 Agent 配置目录。1 个 → 直接安装；多个 → 报错；未探测到 → 回退到 `<cwd>/.agents/skills/`。

> `--dir` 不能与 `--scope` 或 `--agent` 同时使用。

### 安装路径

每个 Agent 有项目级和用户级两个 skills 目录。`--scope user|project` 决定使用哪一个。

| Agent | 项目级路径 | 用户级路径 |
|-------|-----------|-----------|
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

对于自定义路径或不在列表中的 Agent 目录，使用 `--dir` 显式指定安装路径。交互式 user scope 下会与已探测 Agent 目标一同提供 `generic` 目标；当 `--scope user|project` 找不到匹配的 agent 目录时，CLI 会回退到上表的 `_fallback_` 行。

### 安装后的文件结构

```
.codex/skills/pdf-parser/
├── ...                          # 技能包解压后的文件
└── .joyhub/
    └── metadata.json            # 安装元数据
```

`metadata.json` 内容示例：

```json
{
  "registry": "https://skill.xfyun.cn",
  "namespace": "global",
  "slug": "pdf-parser",
  "version": "1.0.0",
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
```

## 本地管理

### 查看已安装技能

```bash
# 列出所有已安装技能
joyhub list

# 按 Agent 过滤
joyhub list --agent codex

# 按多个 Agent 过滤
joyhub list --agent codex --agent claude-code

# 按目录过滤
joyhub list --dir ~/.codex/skills

# JSON 输出
joyhub list --json
```

### 删除技能

```bash
# 裸 slug 删除所有 namespace 中的同名本地安装
joyhub remove pdf-parser

# 显式 namespace 坐标只删除该 namespace
joyhub remove myspace/pdf-parser
joyhub remove @myspace/pdf-parser
joyhub remove myspace--pdf-parser

# 使用 namespace 参数进行等价的精确本地删除
joyhub remove pdf-parser --namespace myspace

# 只删除指定 Agent 的安装
joyhub remove pdf-parser --agent codex

# 删除所有目标（跳过交互确认）
joyhub remove pdf-parser --all

# 删除远程技能（需要认证，会弹出确认提示）
joyhub remove pdf-parser --remote --namespace myspace

# 跳过远程删除确认
joyhub remove pdf-parser --remote --hard --namespace myspace
```

> 参数互斥规则：
> - `--all` 不能与 `--agent` 同时使用
> - `--remote` 不能与 `--agent` 或 `--all` 同时使用
> - 非交互环境下远程删除必须加 `--hard`

### 重建本地清单

```bash
joyhub doctor
```

`doctor` 执行以下操作：

1. 扫描 `<cwd>/.<agent>/skills/<slug>/.joyhub/metadata.json`
2. 按 `registry + namespace + slug` 分组
3. 备份旧的 `inventory.json`（如果存在）
4. 写入新的 `inventory.json`

如果同一技能在不同目标中存在版本冲突，该技能会被跳过并报告。

## 发布

```bash
# 发布目录（自动打包为 zip）
joyhub publish ./my-skill --namespace myspace

# 发布已有的 zip 文件
joyhub publish ./my-skill.zip --namespace myspace

# 指定可见性
joyhub publish ./my-skill --namespace myspace --visibility private
```

可见性选项：
- `public`（默认）— 所有人可见
- `namespace-only` — 仅 namespace 成员可见
- `private` — 仅自己可见

发布成功后会输出技能详情页 URL。

## 自更新

```bash
# 检查是否有新版本
joyhub update --check

# 执行更新
joyhub update
```

更新机制：
- 官方 Skill 与推荐用法固定 `@toolnets/joyhub-cli@0.2.0`，不要改用 `latest`
- 若曾全局安装：`npm install -g @toolnets/joyhub-cli@0.2.0` 或 `bun add -g @toolnets/joyhub-cli@0.2.0`
- 通过 npx 运行：使用 pinned 命令重新拉取 `0.2.0`

## 环境变量

| 变量 | 说明 | 优先级 |
|------|------|--------|
| `JOYHUB_REGISTRY` | 默认 registry URL | 低于 `--registry` 参数 |
| `JOYHUB_TOKEN` | API token | 低于 `--token` 参数，高于存储的 token |

## 本地文件结构

```
~/.joyhub/
├── config.json           # 用户配置（registry、defaultAgent 等）
├── credentials.json      # API tokens（按 registry 存储，权限 0600）
└── inventory.json        # 已安装技能清单
```

### config.json

```json
{
  "registry": "https://skill.xfyun.cn",
  "defaultAgent": "codex",
  "lastUpdateCheckAt": "2026-04-28T06:00:00.000Z"
}
```

### credentials.json

```json
{
  "tokens": {
    "https://skill.xfyun.cn": "sk_xxx",
    "https://skillhub.example.com": "sk_yyy"
  }
}
```

### inventory.json

```json
{
  "items": [
    {
      "registry": "https://skill.xfyun.cn",
      "namespace": "global",
      "slug": "pdf-parser",
      "version": "1.0.0",
      "targets": [
        {
          "agent": "codex",
          "rootDir": "/path/to/project/.codex/skills",
          "installDir": "/path/to/project/.codex/skills/pdf-parser",
          "installedAt": "2026-04-28T06:00:00.000Z"
        }
      ]
    }
  ]
}
```

## JSON 输出

所有命令都支持 `--json` 参数，输出机器可读的 JSON 格式：

```bash
joyhub search pdf --json
joyhub list --json
joyhub whoami --json
joyhub install pdf-parser --json
joyhub remove pdf-parser --json
joyhub doctor --json
```

成功响应格式：

```json
{
  "ok": true,
  ...
}
```

错误响应格式：

```json
{
  "ok": false,
  "message": "error message",
  "exitCode": 2,
  "details": {
    "registry": "https://skill.xfyun.cn",
    "next": "run `joyhub auth ensure`"
  }
}
```

## 退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1 | 通用错误 |
| 2 | 认证失败 |
| 3 | 网络错误 |
| 4 | 文件系统错误 |
| 5 | 参数错误 |

## 命令参考

### help

```bash
joyhub help
joyhub help install
```

显示帮助信息。

### version

```bash
joyhub version
joyhub version --json
```

显示 CLI 版本。

### auth ensure

```bash
joyhub auth ensure [--registry <url>] [--json]
```

校验本地登录态；无效时发起浏览器 Device Flow。官方 Skill `find-skills` / `share-skill` 每次执行前调用 `joyhub auth ensure --json`。

### login

```bash
joyhub login [--token <token>] [--registry <url>] [--json]
```

保存 token 和 registry 配置。未提供 `--token` 时走 Device Flow。

### logout

```bash
joyhub logout [--registry <url>] [--json]
```

删除指定 registry 的 token。

### whoami

```bash
joyhub whoami [--registry <url>] [--token <token>] [--json]
```

验证当前 token 并显示用户信息。

### search

```bash
joyhub search <query> [--registry <url>] [--limit <n>] [--json]
```

搜索已发布的技能。

### install

```bash
joyhub install <coordinate> [options]
```

`<coordinate>` 支持裸 slug（`my-skill`，解析为 `global/my-skill`）以及
`team/my-skill`、`@team/my-skill`、`team--my-skill` 三种等价的显式
namespace 形式。裸 slug 可通过 `--namespace team` 选择非 global namespace；
显式坐标可以同时传入相同的 `--namespace`，但冲突值会作为用法错误被拒绝。

选项：
- `--scope <user|project>` — 安装范围（不传时：TTY 模式下交互式询问，非 TTY 模式沿用现有探测逻辑）
- `--namespace <slug>` — 为裸 slug 指定 namespace
- `--version <v>` — 版本（默认最新版本）
- `--agent <profile>` — Agent 配置（可重复）
- `--dir <path>` — 自定义安装目录（与 `--scope`、`--agent` 互斥）
- `--force` — 覆盖已存在的安装
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

### list

```bash
joyhub list [options]
```

选项：
- `--agent <profile>` — 按 Agent 过滤（可重复）
- `--dir <path>` — 按目录过滤
- `--registry <url>` — Registry URL
- `--json` — JSON 输出

### remove

```bash
joyhub remove <coordinate> [options]
```

选项：
- `--agent <profile>` — 按 Agent 过滤（可重复）
- `--all` — 删除所有目标
- `--remote` — 删除远程技能
- `--hard` — 跳过远程删除确认
- `--namespace <slug>` — 本地或远程删除的 namespace
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

显式命名空间坐标（`team/my-skill`、`@team/my-skill`、`team--my-skill`）或
`--namespace team` 只删除该 namespace 中的本地安装。为保持兼容，裸 slug
会删除当前 registry 中所有 namespace 下的同名本地安装。

### doctor

```bash
joyhub doctor [--json]
```

扫描项目目录，重建本地清单。

### publish

```bash
joyhub publish <path> [options]
```

选项：
- `--namespace <slug>` — Namespace
- `--visibility <v>` — 可见性（`public` | `namespace-only` | `private`）
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

### update

```bash
joyhub update [--check] [--json]
```

检查或执行 CLI 自更新。

## 安全说明

- Token 只存储在用户目录 `~/.joyhub/credentials.json`
- 在 Linux/macOS 上，凭据文件权限自动设置为 `0600`
- 不会将 token 写入任何项目本地文件
- 远程删除操作需要显式确认或 `--hard` 参数
- `remove` 命令会验证路径安全性，防止删除非技能目录

## 故障排查

### 认证失败

```bash
# 验证当前登录态
joyhub whoami

# 重新绑定（浏览器授权，无需复制 Token）
joyhub auth ensure
```

### 网络错误

```bash
# 检查 registry 是否可访问
curl https://skill.xfyun.cn/api/cli/v1/skills/search?q=test&limit=1

# 使用其他 registry
joyhub search test --registry https://skillhub.example.com
```

### 安装目录冲突

```bash
# 使用 --force 覆盖
joyhub install pdf-parser --force

# 或先删除再安装
joyhub remove pdf-parser
joyhub install pdf-parser
```

### 清单损坏

```bash
# 重建清单
joyhub doctor
```

## 本地开发验证

如果你在本地开发 SkillHub，可以这样验证 CLI：

```bash
# 1. 构建 CLI
cd cli
bun install
bun run build
bun link

# 2. 启动本地后端
cd ..
make dev-all

# 3. 配置 CLI 连接本地服务（Linux/macOS）
export JOYHUB_REGISTRY=http://localhost:8080

# Windows PowerShell:
# $env:JOYHUB_REGISTRY="http://localhost:8080"

# Windows CMD:
# set JOYHUB_REGISTRY=http://localhost:8080

# 4. 测试命令
joyhub search test
joyhub install example-skill --agent codex
joyhub list
```

## 相关链接

- [SkillHub 主页](https://skill.xfyun.cn)
- [GitHub 仓库](https://github.com/iflytek/skillhub)
- [问题反馈](https://github.com/iflytek/skillhub/issues)

## 许可证

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.
