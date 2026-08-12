package com.iflytek.skillhub.dto;

/** Current category assignment in the unified search projection. */
public record ResourceCategoryResponse(
        String resourceType,
        Long resourceId,
        String categoryCode,
        String categorySource
) {
}
