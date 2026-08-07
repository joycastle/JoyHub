package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Unified owner-facing resource card shared by Skill Registry and the static Catalog.
 * Source-specific detail pages remain responsible for their specialised payloads.
 */
public record ResourceSummaryResponse(
        String resourceId,
        String sourceType,
        Long sourceId,
        String kind,
        String slug,
        String name,
        String summary,
        String namespace,
        String status,
        String version,
        String versionStatus,
        String visibility,
        Long downloadCount,
        Integer starCount,
        Integer ratingCount,
        boolean canManage,
        Instant updatedAt,
        Set<String> actions,
        boolean favorited
) {
}
