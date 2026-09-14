ALTER TABLE skill
    ADD COLUMN sharing_restricted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN skill.sharing_restricted IS
    'When true, skill reads are limited to the maintenance namespace and explicit skill_visible_namespace grants.';
