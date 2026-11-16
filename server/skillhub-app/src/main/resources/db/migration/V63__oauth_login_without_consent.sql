-- Existing CLI/MCP installations must also return directly after JoyHub login.
-- Preserve PKCE and all other per-client settings and existing grants.
UPDATE oauth2_registered_client
SET client_settings = jsonb_set(
    client_settings::jsonb,
    '{settings.client.require-authorization-consent}',
    'false'::jsonb
)::text
WHERE client_settings::jsonb ->> 'settings.client.require-authorization-consent' = 'true';
