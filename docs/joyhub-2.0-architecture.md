# JoyHub 2.0 architecture and parallel development boundaries

## 1. Goal

JoyHub 2.0 keeps the mature Skill registry intact and adds a catalog bounded context for the
other company AI capabilities: Agents, plugins, MCP/tool servers, online tools, internal services,
knowledge bases, templates, and resource packs. A catalog resource owns one canonical entry and
one Markdown document. Other pages link to that entry instead of copying its content.

The first release is a modular monolith. It provides strong code and data ownership boundaries
without introducing distributed transactions or operationally expensive microservices.

## 2. Bounded contexts

| Context | Owns | Does not own |
|---|---|---|
| Skill Registry | `SKILL.md` packages, versions, scanning, downloads, ClawHub compatibility | Generic online tools and Agents |
| Catalog | Agent/tool/plugin/MCP/service metadata, one document, artifact metadata, relations, lifecycle | Skill package lifecycle, users, departments |
| Organization | Namespaces used as first-release departments and membership | Catalog content |
| Identity & Access | Login, platform roles, request principal | Catalog visibility rules |
| Search Composition | Unified result projection across Catalog and Skill Registry | Source-of-truth records |

## 3. Backend module boundaries

```text
skillhub-app ───────► skillhub-catalog
      │                     ▲
      ├────────────► skillhub-domain (Skill + Namespace)
      ├────────────► skillhub-storage
      └────────────► skillhub-infra
                            │
                            ├────► skillhub-catalog (JPA adapter)
                            └────► skillhub-domain
```

- `skillhub-catalog`: catalog aggregate, value enums, lifecycle and visibility policies, repository
  ports. It has no dependency on the existing Skill domain or infrastructure.
- `skillhub-infra`: JPA implementations for both the existing domain and Catalog ports.
- `skillhub-app`: HTTP controllers, DTOs, Catalog/Skill/Namespace bridges, object-storage workflow,
  and unified read-model composition.
- Catalog never imports Skill, Namespace, auth, storage, or web DTO classes. Cross-context links are
  IDs and are resolved by adapters in `skillhub-app`.

## 4. Frontend ownership

```text
web/src/entities/catalog-resource/   # Resource display types and cards
web/src/features/catalog/            # Queries, mutations, forms, filters
web/src/pages/agents.tsx              # Agent Center
web/src/pages/tools.tsx               # Tool Center
web/src/pages/catalog-resource.tsx    # Unified detail
web/src/pages/dashboard/catalog.tsx   # Maintainer workspace
```

Existing Skill pages and features remain independent. `/search` composes results from both feature
sets but does not become the owner of either API.

## 5. Catalog aggregate

`CatalogResource` is the canonical entry. It contains:

- identity: slug, name, kind, icon;
- discovery: summary, scenarios, tags, primary department;
- usage: access URL, package artifact metadata, version;
- documentation: one Markdown document stored on the resource;
- governance: owner, maintenance status, lifecycle status, visibility scope;
- relations: related catalog resource IDs and related Skill IDs;
- audit timestamps: created, updated, published.

Lifecycle is intentionally small for v1: `DRAFT -> PUBLISHED -> OFFLINE`, with an offline resource
allowed to be republished. Publishing is open to any authenticated employee. Only the owner or a
super administrator can update, transfer, publish, or take down an entry.

Visibility is either `COMPANY` or `DEPARTMENTS`. Department IDs refer to existing Namespace IDs.
The owner and super administrators retain access to drafts and restricted entries.

## 6. Tables and migration ownership

Catalog owns tables prefixed with `catalog_`:

- `catalog_resource`
- `catalog_resource_tag`
- `catalog_resource_scenario`
- `catalog_resource_visible_namespace`
- `catalog_resource_relation`
- `catalog_resource_skill_relation`

Only the Catalog workstream changes these tables. Organization and Skill workstreams expose stable
ID-based APIs and do not add foreign keys into Catalog tables, which prevents migration coupling.

## 7. API contract

The browser API is rooted at `/api/web/catalog`:

- `GET /resources`: visible published resources with query/type/department filters;
- `GET /resources/{slug}`: viewer-specific detail projection;
- `GET /me/resources`: resources maintained by the current user;
- `POST /resources`, `PUT /resources/{slug}`: create/update metadata and document;
- `POST /resources/{slug}/publish`, `POST /resources/{slug}/offline`;
- `POST /resources/{slug}/artifact`, `GET /resources/{slug}/artifact`.

Controllers only bind transport data. `CatalogResourceAppService` orchestrates the Catalog domain,
Organization lookups, Skill link validation, and object storage. `CatalogQueryRepository` owns the
viewer-specific read model and joins.

## 8. Parallel workstreams

| Workstream | Primary ownership | Stable dependency |
|---|---|---|
| A. Catalog core | `skillhub-catalog`, `catalog_*` migration | None |
| B. Persistence/API | Infra adapter, app service, controller, DTOs | Catalog ports |
| C. Agent Center | Agent page and Catalog read hooks | `GET /catalog/resources?center=AGENT` |
| D. Tool Center | Tool page, cards, filters, importer UI | `GET /catalog/resources?center=TOOL` |
| E. Publishing | Maintainer pages and artifact upload | Catalog command endpoints |
| F. Unified search | Search result adapter and `/search` composition | Read-only Skill and Catalog APIs |
| G. Organization | Department membership/admin enhancements | Namespace IDs and membership projection |

Each workstream should avoid editing another workstream's owned directory. Contract changes land
first, followed by generated OpenAPI types, so frontend branches can rebase on a stable schema.

## 9. Initial import policy

The first local seed imports the eight live Bingo Frenzy tools from `192.168.6.105`. Imported
resources remain normal Catalog entries and can later be transferred to real maintainers. Importers
must be idempotent and update only entries with their own source key; they must not overwrite
employee-authored content sharing a similar display name.

## 10. Deferred items

- automatic Agent installation and device management;
- automated MCP/plugin configuration;
- service hosting and deployment;
- a dedicated organization directory synchronized from HR/Feishu;
- Catalog version history beyond the current version label;
- asynchronous cross-context search indexing and analytics events.
