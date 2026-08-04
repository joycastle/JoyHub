package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Set;

public record CatalogResourceSummaryResponse(
        Long id,
        String slug,
        String name,
        String summary,
        String kind,
        String icon,
        String accessUrl,
        String version,
        CatalogDepartmentResponse department,
        CatalogOwnerResponse owner,
        String status,
        String maintenanceStatus,
        String visibilityScope,
        Set<String> scenarios,
        Set<String> tags,
        boolean artifactAvailable,
        Instant updatedAt
) {
}
