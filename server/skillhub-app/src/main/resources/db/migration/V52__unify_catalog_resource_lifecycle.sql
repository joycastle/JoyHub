ALTER TABLE catalog_resource DROP CONSTRAINT ck_catalog_resource_status;

ALTER TABLE catalog_resource
    ADD CONSTRAINT ck_catalog_resource_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE', 'ARCHIVED'));
