package com.iflytek.skillhub.infra.jpa;

import java.util.Locale;

/** Fixed, cross-resource category pool used by the unified search projection. */
public enum ResourceCategoryCode {
    GAME_DEV_QA,
    UA_MONETIZATION,
    CREATIVE_MEDIA,
    DATA_ANALYTICS,
    COLLAB_PRODUCTIVITY,
    AI_ENGINEERING,
    INTEGRATION_AUTOMATION,
    GENERAL_KNOWLEDGE,
    OTHER;

    /** Converts AI/user input to a valid persisted code; unknown or missing values become OTHER. */
    public static ResourceCategoryCode fromExternal(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
