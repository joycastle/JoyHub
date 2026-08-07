CREATE TABLE resource_stat (
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    use_count BIGINT NOT NULL DEFAULT 0,
    download_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_resource_stat PRIMARY KEY (source_type, source_id),
    CONSTRAINT ck_resource_stat_source CHECK (source_type IN ('SKILL', 'CATALOG')),
    CONSTRAINT ck_resource_stat_counts CHECK (
        view_count >= 0 AND use_count >= 0 AND download_count >= 0
    )
);

CREATE INDEX idx_resource_stat_updated_at ON resource_stat (updated_at DESC);
