CREATE TABLE deployable_application (
    id BIGSERIAL PRIMARY KEY,
    catalog_resource_id BIGINT NOT NULL UNIQUE REFERENCES catalog_resource(id),
    deployment_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stable_url VARCHAR(1024) NOT NULL,
    current_release_id BIGINT,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deployment_release (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES deployable_application(id),
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    artifact_reference VARCHAR(512) NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    failure_code VARCHAR(96),
    failure_summary VARCHAR(1200),
    created_by VARCHAR(128) NOT NULL,
    deployed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deployment_release_version UNIQUE (application_id, version)
);

ALTER TABLE deployable_application
    ADD CONSTRAINT fk_deployable_application_current_release
    FOREIGN KEY (current_release_id) REFERENCES deployment_release(id);

CREATE TABLE deployment_job (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES deployable_application(id),
    release_id BIGINT REFERENCES deployment_release(id),
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    result_code VARCHAR(96),
    result_summary VARCHAR(1200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_deployment_release_application ON deployment_release(application_id, created_at DESC);
CREATE INDEX idx_deployment_job_application ON deployment_job(application_id, created_at DESC);
CREATE UNIQUE INDEX uq_deployment_job_running
    ON deployment_job(application_id) WHERE status = 'RUNNING';
