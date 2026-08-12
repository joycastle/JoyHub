ALTER TABLE resource_search_document
    ADD COLUMN category_code VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN category_source VARCHAR(16) NOT NULL DEFAULT 'AI',
    ADD CONSTRAINT ck_resource_search_document_category_code CHECK (
        category_code IN (
            'GAME_DEV_QA', 'UA_MONETIZATION', 'CREATIVE_MEDIA', 'DATA_ANALYTICS',
            'COLLAB_PRODUCTIVITY', 'AI_ENGINEERING', 'INTEGRATION_AUTOMATION',
            'GENERAL_KNOWLEDGE', 'OTHER'
        )
    ),
    ADD CONSTRAINT ck_resource_search_document_category_source CHECK (category_source IN ('AUTHOR', 'AI'));

CREATE INDEX idx_resource_search_document_category
    ON resource_search_document (category_code, search_enabled, resource_type);

-- Existing documents already have useful search profiles. Queue them for one asynchronous
-- regeneration so only the new category is filled; the current profile remains searchable
-- until generation succeeds.
UPDATE resource_search_document
SET generation_status = 'PENDING'
WHERE category_source = 'AI';
