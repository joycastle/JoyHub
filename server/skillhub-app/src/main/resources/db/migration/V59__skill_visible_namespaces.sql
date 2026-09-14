-- Additional departments that may discover and use a skill.
-- The skill.namespace_id remains the single maintenance namespace; these rows
-- only grant read/download access to members of the selected namespaces.
CREATE TABLE skill_visible_namespace (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_visible_namespace UNIQUE (skill_id, namespace_id)
);

CREATE INDEX idx_skill_visible_namespace_namespace
 ON skill_visible_namespace (namespace_id, skill_id);
