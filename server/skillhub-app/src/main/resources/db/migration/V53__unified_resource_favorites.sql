CREATE TABLE resource_favorite (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_favorite UNIQUE (source_type, source_id, user_id),
    CONSTRAINT ck_resource_favorite_source CHECK (source_type IN ('CATALOG'))
);

CREATE INDEX idx_resource_favorite_source ON resource_favorite (source_type, source_id);
CREATE INDEX idx_resource_favorite_user ON resource_favorite (user_id, created_at DESC);
