package com.iflytek.skillhub.service;

import com.iflytek.skillhub.exception.BadRequestException;

/** Stable transport reference used by the unified resource API. */
public record ResourceReference(String sourceType, Long sourceId) {
    public static ResourceReference parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("error.resource.reference.invalid", raw);
        }
        String[] parts = raw.trim().split(":", 2);
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new BadRequestException("error.resource.reference.invalid", raw);
        }
        try {
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return new ResourceReference(parts[0].trim().toUpperCase(java.util.Locale.ROOT), id);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("error.resource.reference.invalid", raw);
        }
    }

    public String value() {
        return sourceType.toLowerCase(java.util.Locale.ROOT) + ":" + sourceId;
    }
}
