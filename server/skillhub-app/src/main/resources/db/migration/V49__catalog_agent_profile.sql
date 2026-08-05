ALTER TABLE catalog_resource
    ADD COLUMN agent_usage_boundary TEXT,
    ADD COLUMN agent_input_guide TEXT,
    ADD COLUMN agent_output_guide TEXT,
    ADD COLUMN agent_support_contact VARCHAR(256);

CREATE TABLE catalog_resource_agent_example_prompt (
    resource_id BIGINT NOT NULL REFERENCES catalog_resource(id) ON DELETE CASCADE,
    prompt VARCHAR(1000) NOT NULL,
    PRIMARY KEY (resource_id, prompt)
);
