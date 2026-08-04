package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CatalogResourceDetailResponse(
        Long id,
        String slug,
        String name,
        String summary,
        String kind,
        String icon,
        String accessUrl,
        String documentation,
        String version,
        CatalogDepartmentResponse department,
        CatalogOwnerResponse owner,
        String status,
        String maintenanceStatus,
        String visibilityScope,
        List<CatalogDepartmentResponse> visibleDepartments,
        Set<String> scenarios,
        Set<String> tags,
        List<CatalogResourceSummaryResponse> relatedResources,
        List<CatalogRelatedSkillResponse> relatedSkills,
        boolean artifactAvailable,
        String artifactFilename,
        Long artifactSize,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
}
