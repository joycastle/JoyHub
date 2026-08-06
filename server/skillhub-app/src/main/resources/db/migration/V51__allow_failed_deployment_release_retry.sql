ALTER TABLE deployment_release
    DROP CONSTRAINT IF EXISTS uq_deployment_release_version;

CREATE UNIQUE INDEX uq_deployment_release_non_failed_version
    ON deployment_release (application_id, version)
    WHERE status <> 'FAILED';
