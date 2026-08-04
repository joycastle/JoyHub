ALTER TABLE namespace
    ADD COLUMN external_provider VARCHAR(32),
    ADD COLUMN external_id VARCHAR(128);

CREATE UNIQUE INDEX uk_namespace_external_identity
    ON namespace (external_provider, external_id)
    WHERE external_provider IS NOT NULL AND external_id IS NOT NULL;
