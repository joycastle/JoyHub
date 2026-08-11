-- One durable search projection for Skills, Agents, and Tools.  Business aggregates remain separate.
CREATE TABLE resource_search_document (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(16) NOT NULL,
    resource_id BIGINT NOT NULL,
    namespace_id BIGINT,
    owner_id VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    summary TEXT,
    capabilities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    scenarios_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    inputs_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    outputs_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    search_terms_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    access_mode VARCHAR(16) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    company_relevance VARCHAR(16) NOT NULL DEFAULT 'GENERAL',
    search_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    profile_text TEXT NOT NULL DEFAULT '',
    raw_documentation TEXT NOT NULL DEFAULT '',
    semantic_vector TEXT,
    source_hash VARCHAR(64) NOT NULL,
    generation_status VARCHAR(16) NOT NULL DEFAULT 'BASIC',
    generator_model VARCHAR(128),
    prompt_version VARCHAR(32),
    generated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_search_document UNIQUE (resource_type, resource_id),
    CONSTRAINT ck_resource_search_document_type CHECK (resource_type IN ('SKILL', 'AGENT', 'TOOL')),
    CONSTRAINT ck_resource_search_document_access CHECK (access_mode IN ('INSTALL', 'OPEN', 'DOWNLOAD')),
    CONSTRAINT ck_resource_search_document_generation CHECK (generation_status IN ('BASIC', 'PENDING', 'READY', 'FAILED')),
    CONSTRAINT ck_resource_search_document_relevance CHECK (company_relevance IN ('CORE', 'SUPPORTING', 'GENERAL', 'IRRELEVANT'))
);

ALTER TABLE resource_search_document ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(slug, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(capabilities_json::text, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(outputs_json::text, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(search_terms_json::text, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(scenarios_json::text, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(profile_text, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(raw_documentation, '')), 'D')
) STORED;

CREATE INDEX idx_resource_search_document_vector ON resource_search_document USING GIN (search_vector);
CREATE INDEX idx_resource_search_document_filter ON resource_search_document (search_enabled, resource_type, access_mode);
CREATE INDEX idx_resource_search_document_namespace ON resource_search_document (namespace_id);
CREATE INDEX idx_resource_search_document_generation ON resource_search_document (generation_status, updated_at);

-- Existing resources receive an immediately searchable BASIC document.  AI enrichment is asynchronous.
INSERT INTO resource_search_document (
    resource_type, resource_id, namespace_id, owner_id, title, slug, summary,
    access_mode, visibility, status, profile_text, raw_documentation, source_hash, generation_status)
SELECT 'SKILL', s.id, s.namespace_id, s.owner_id,
       COALESCE(NULLIF(s.localized_display_name, ''), NULLIF(s.display_name, ''), s.slug),
       s.slug, COALESCE(NULLIF(s.localized_summary, ''), s.summary, ''),
       'INSTALL', s.visibility::text, s.status::text,
       concat_ws(E'\n', s.localized_display_name, s.display_name, s.localized_summary, s.summary),
       COALESCE(d.search_text, ''),
       md5(concat_ws('|', s.slug, s.display_name, s.summary, d.search_text)), 'PENDING'
FROM skill s
LEFT JOIN skill_search_document d ON d.skill_id = s.id
WHERE s.status = 'ACTIVE' AND COALESCE(s.hidden, FALSE) = FALSE
ON CONFLICT (resource_type, resource_id) DO NOTHING;

INSERT INTO resource_search_document (
    resource_type, resource_id, namespace_id, owner_id, title, slug, summary,
    scenarios_json, search_terms_json, access_mode, visibility, status,
    profile_text, raw_documentation, source_hash, generation_status)
SELECT CASE WHEN c.kind = 'AGENT' THEN 'AGENT' ELSE 'TOOL' END,
       c.id, c.primary_namespace_id, c.owner_id, c.name, c.slug, c.summary,
       COALESCE((SELECT jsonb_agg(s.scenario ORDER BY s.scenario)
                 FROM catalog_resource_scenario s WHERE s.resource_id = c.id), '[]'::jsonb),
       COALESCE((SELECT jsonb_agg(t.tag ORDER BY t.tag)
                 FROM catalog_resource_tag t WHERE t.resource_id = c.id), '[]'::jsonb),
       CASE WHEN NULLIF(c.access_url, '') IS NOT NULL THEN 'OPEN'
            WHEN c.artifact_storage_key IS NOT NULL THEN 'DOWNLOAD' ELSE 'OPEN' END,
       c.visibility_scope::text, c.status::text,
       concat_ws(E'\n', c.name, c.summary, c.documentation), COALESCE(c.documentation, ''),
       md5(concat_ws('|', c.slug, c.name, c.summary, c.documentation)), 'PENDING'
FROM catalog_resource c
WHERE c.status = 'PUBLISHED'
ON CONFLICT (resource_type, resource_id) DO NOTHING;
