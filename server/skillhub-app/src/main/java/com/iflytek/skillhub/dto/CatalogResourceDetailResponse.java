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
        String agentUsageBoundary,
        String agentInputGuide,
        String agentOutputGuide,
        String agentSupportContact,
        Set<String> agentExamplePrompts,
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
        Instant publishedAt,
        String categoryCode,
        String categorySource
) {
    public CatalogResourceDetailResponse(
            Long id, String slug, String name, String summary, String kind, String icon, String accessUrl,
            String documentation, String version, String agentUsageBoundary, String agentInputGuide,
            String agentOutputGuide, String agentSupportContact, Set<String> agentExamplePrompts,
            CatalogDepartmentResponse department, CatalogOwnerResponse owner, String status,
            String maintenanceStatus, String visibilityScope, List<CatalogDepartmentResponse> visibleDepartments,
            Set<String> scenarios, Set<String> tags, List<CatalogResourceSummaryResponse> relatedResources,
            List<CatalogRelatedSkillResponse> relatedSkills, boolean artifactAvailable, String artifactFilename,
            Long artifactSize, boolean canManage, Instant createdAt, Instant updatedAt, Instant publishedAt) {
        this(id, slug, name, summary, kind, icon, accessUrl, documentation, version, agentUsageBoundary,
                agentInputGuide, agentOutputGuide, agentSupportContact, agentExamplePrompts, department, owner,
                status, maintenanceStatus, visibilityScope, visibleDepartments, scenarios, tags, relatedResources,
                relatedSkills, artifactAvailable, artifactFilename, artifactSize, canManage, createdAt, updatedAt,
                publishedAt, "OTHER", "AI");
    }
}
