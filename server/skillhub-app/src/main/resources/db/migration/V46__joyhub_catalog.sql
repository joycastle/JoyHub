CREATE TABLE catalog_resource (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(96) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    summary VARCHAR(1200) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    icon VARCHAR(256),
    access_url VARCHAR(1024),
    documentation TEXT,
    version VARCHAR(64),
    primary_namespace_id BIGINT,
    owner_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    maintenance_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    visibility_scope VARCHAR(32) NOT NULL DEFAULT 'COMPANY',
    artifact_storage_key VARCHAR(512),
    artifact_filename VARCHAR(256),
    artifact_content_type VARCHAR(160),
    artifact_size BIGINT,
    source_key VARCHAR(160) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_catalog_resource_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT ck_catalog_resource_visibility CHECK (visibility_scope IN ('COMPANY', 'DEPARTMENTS')),
    CONSTRAINT ck_catalog_resource_maintenance CHECK (maintenance_status IN ('ACTIVE', 'MAINTENANCE', 'DEPRECATED'))
);

CREATE INDEX idx_catalog_resource_discovery
    ON catalog_resource (status, kind, updated_at DESC);
CREATE INDEX idx_catalog_resource_owner
    ON catalog_resource (owner_id, updated_at DESC);
CREATE INDEX idx_catalog_resource_primary_namespace
    ON catalog_resource (primary_namespace_id);

CREATE TABLE catalog_resource_visible_namespace (
    resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL,
    PRIMARY KEY (resource_id, namespace_id)
);
CREATE INDEX idx_catalog_visible_namespace_namespace
    ON catalog_resource_visible_namespace (namespace_id, resource_id);

CREATE TABLE catalog_resource_scenario (
    resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    scenario VARCHAR(96) NOT NULL,
    PRIMARY KEY (resource_id, scenario)
);
CREATE INDEX idx_catalog_scenario_value ON catalog_resource_scenario (scenario);

CREATE TABLE catalog_resource_tag (
    resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    tag VARCHAR(64) NOT NULL,
    PRIMARY KEY (resource_id, tag)
);
CREATE INDEX idx_catalog_tag_value ON catalog_resource_tag (tag);

CREATE TABLE catalog_resource_relation (
    source_resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    target_resource_id BIGINT NOT NULL,
    PRIMARY KEY (source_resource_id, target_resource_id),
    CONSTRAINT ck_catalog_resource_relation_not_self CHECK (source_resource_id <> target_resource_id)
);

CREATE TABLE catalog_resource_skill_relation (
    resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (resource_id, skill_id)
);
