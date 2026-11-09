-- Retry metadata is independent of updated_at, which is refreshed by the index synchronizer.
ALTER TABLE resource_search_document
    ADD COLUMN generation_attempts INTEGER NOT NULL DEFAULT 0 CHECK (generation_attempts >= 0),
    ADD COLUMN generation_error_code VARCHAR(64),
    ADD COLUMN generation_next_retry_at TIMESTAMPTZ,
    ADD COLUMN generation_lease_token VARCHAR(36),
    ADD COLUMN generation_lease_until TIMESTAMPTZ;

-- Give existing failed jobs a bounded recovery budget without changing their category.
UPDATE resource_search_document
SET generation_error_code = 'LEGACY_UNKNOWN', generation_next_retry_at = CURRENT_TIMESTAMP
WHERE generation_status = 'FAILED';

CREATE INDEX idx_resource_profile_retry
    ON resource_search_document (generation_status, generation_next_retry_at, generation_lease_until)
    WHERE search_enabled = TRUE;
