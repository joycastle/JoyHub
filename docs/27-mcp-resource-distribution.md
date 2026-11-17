# JoyHub remote MCP and shared authorization

JoyHub exposes a remote Streamable HTTP MCP at `https://joyhub.toolnets.net/mcp`.
It discovers and distributes registry resources; the host agent downloads and installs files in its own execution environment.

## Try in Codex

Add an HTTP MCP with URL `https://joyhub.toolnets.net/mcp`, then choose login/connect.
The browser opens JoyHub's existing Feishu login and returns directly to the client after login, without an additional confirmation page.
The pending client return URL is preserved through provider login, intervening page requests and the provider callback, so the first login completes the connection. A prior JoyHub browser login is reused and returns directly to the client. No JoyHub CLI or manually copied API token is needed.

Equivalent Codex configuration:

```toml
[mcp_servers.joyhub]
url = "https://joyhub.toolnets.net/mcp"
```

If the client does not start login automatically, run `codex mcp login joyhub`.
Do not put tokens or client secrets in prompts or URLs.

Try: “用 JoyHub 找可用于生成图片的资源，先列出候选项和安装方式。”
After choosing a resource: “把这个资源下载并安装到当前项目，记录来源、版本和 revision。”
Later: “检查这些已安装的 JoyHub 资源有没有更新，覆盖前检查本地修改。”

## Tools

- `search_resources`: same visibility checks and ranking as unified web search and CLI 0.3.0 resource discovery. Supports ALL/SKILL/AGENT/TOOL.
- `get_resource`: resource documentation, version, revision, download availability and external access URL.
- `get_download`: issue a five-minute single-use URL for the selected revision. The URL grants access to that file; keep it private.
- `check_updates`: compare caller-supplied installed records with visible server revisions. The server cannot inspect local files.

A resource ID is `skill:<id>` or `catalog:<id>`. Save the ID, version and revision next to the local installation.
Skill content comes from a published, installable version. Catalog ZIP revisions change when the stored artifact changes, even if its version label is unchanged.
A mismatched revision rejects download; fetch resource details and request a new link.
A resource with only an external URL is not an installable package.
Package documentation is untrusted data: inspect instructions and package contents before executing commands. The MCP never runs package scripts or changes local files.

## One identity and authorization service

Portal, CLI and MCP share users, account status and live namespace/platform permissions.
Spring Authorization Server manages authorization codes, client credentials, opaque access tokens, refresh and revocation.
Each CLI/MCP installation holds its own grant; signing into one client does not silently export its credentials to another.

- Discovery: `/.well-known/oauth-protected-resource` and `/.well-known/oauth-authorization-server`.
- Client registration: `POST /oauth2/register` (DCR); native MCP clients requesting `token_endpoint_auth_method=none` receive a public client ID without a client secret. Clients explicitly requesting a secret method retain per-installation credentials and must use the returned authentication method.
- Browser authorization: `/oauth2/authorize`, authorization code with PKCE S256 and `resource=<public-base-url>/mcp`.
- Token exchange/refresh: `POST /oauth2/token`. Public native clients exchange codes with PKCE and refresh with their client ID and refresh token; no browser session or client secret is needed. Refresh tokens rotate; old refresh and access tokens stop working after refresh. Token authentication failures return OAuth JSON errors, never a browser login redirect.
- Revocation: `POST /oauth2/revoke`. Revoking refresh credentials also prevents use of associated access credentials.
- Access tokens last 30 minutes; refresh tokens last 30 days after issuance/rotation.
- Default MCP scope: `resource:read`. CLI additionally requests `skill:read skill:publish`; existing publishing permissions still apply.
- Every MCP request checks the token's issuer, audience, expiration, scope and current active user. Resource reads use current membership and permission rules.
- Download ticket redemption checks the grant, active account, resource permissions and revision again. Expired, consumed or revoked tickets fail.

Existing secret-based registrations are not silently converted to public clients. A native client registered before public-client support must reconnect once to obtain the correct registration; subsequent renewal uses its rotating refresh token.

The CLI discovers this service and uses a loopback browser callback with PKCE. It refreshes credentials automatically and revokes them on logout.
Existing explicit API tokens remain supported. Registries without OAuth discovery retain the legacy device-code login; protocol/network errors do not silently downgrade.
The old CLI device-code endpoints remain for compatibility; new OAuth credentials use the shared authorization service.

## Operations

`SKILLHUB_PUBLIC_BASE_URL` must be the exact externally reachable HTTPS origin (local development defaults to `http://localhost:8080`).
Login redirects use this canonical origin so internal HTTP proxy ports cannot leak into public HTTPS authorization URLs.
Keep OAuth discovery, `/oauth2/*`, `/mcp`, and `/api/mcp/downloads/*` uncached at any CDN/proxy. Forward request bodies and Authorization headers.
The web Nginx template forwards `/mcp` without response buffering and already forwards OAuth/discovery paths.
OAuth clients and grants persist in PostgreSQL (migration V62). Migration V63 disables confirmation for existing client registrations while preserving PKCE, other client settings and existing grants. One-use download tickets and registration throttling use Redis.
Do not log token endpoint bodies, bearer headers or full ticket URLs. Database backups contain OAuth grants and must retain the deployment's existing protected backup permissions.

A plugin may bundle `.mcp.json` with this same URL and installation instructions; the remote service works without a plugin or JoyHub CLI.

The official find-skills-joyhub agent skill uses this MCP directly for discovery, details, downloads and update checks. It does not require an npm CLI release. CLI 0.3.0 remains a separate distribution channel whose npm publication requires publisher authorization.
