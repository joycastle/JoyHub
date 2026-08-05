ALTER TABLE skill ADD COLUMN localized_display_name VARCHAR(200);
ALTER TABLE skill ADD COLUMN localized_summary TEXT;
ALTER TABLE skill ADD COLUMN localization_source_hash VARCHAR(64);
