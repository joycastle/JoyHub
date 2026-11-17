## 0.3.0

- Search Skills, Agents and tool packages through the same resource discovery endpoint as MCP.
- Include resource IDs, kinds and names in search results. Catalog resources are never rendered as Skill coordinates.
- Preserve the legacy Skill client API for callers requiring only Skills.

# Changelog

All notable CLI behavior changes are documented in this file.

## Unreleased

### Added

- Reuse JoyHub's shared OAuth authorization service for browser login with PKCE S256,
  automatic credential refresh and revocation on logout. Keep explicit API tokens
  and device-code login for registries without OAuth discovery compatible.
- Serialize credential refresh across processes and atomically persist credentials
  with owner-only file permissions.

### Fixed

- Resolve `namespace/slug`, `@namespace/slug`, and `namespace--slug`
  coordinates against their declared namespace instead of silently falling
  back to `global`.
- Reject a namespaced coordinate combined with a conflicting `--namespace`
  value; a matching value remains valid.
- Limit local removal with a namespaced coordinate or explicit `--namespace`
  to the matching namespace, preventing collateral deletion of same-slug
  installations in other namespaces. Bare-slug removal retains its existing
  cross-namespace behavior for compatibility.
- Preserve public registry `msg` and `requestId` fields for unsuccessful
  responses. HTTP 403 without a public message now reports the neutral
  `access denied` fallback instead of assuming the token lacks scope.
