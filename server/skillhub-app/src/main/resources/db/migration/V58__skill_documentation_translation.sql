CREATE TABLE skill_documentation_translation (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    path VARCHAR(512) NOT NULL,
    language VARCHAR(16) NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    markdown TEXT NOT NULL,
    model VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (version_id, path, language)
);

CREATE INDEX idx_skill_documentation_translation_skill
    ON skill_documentation_translation (skill_id);
