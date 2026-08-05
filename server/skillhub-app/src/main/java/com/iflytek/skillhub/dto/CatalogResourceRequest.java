package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.catalog.domain.CatalogMaintenanceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CatalogResourceRequest(
        @Size(max = 96) String slug,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 1200) String summary,
        @NotNull CatalogResourceKind kind,
        @Size(max = 256) String icon,
        @Size(max = 1024) String accessUrl,
        String documentation,
        @Size(max = 64) String version,
        String agentUsageBoundary,
        String agentInputGuide,
        String agentOutputGuide,
        @Size(max = 256) String agentSupportContact,
        Set<@Size(max = 1000) String> agentExamplePrompts,
        Long primaryDepartmentId,
        CatalogMaintenanceStatus maintenanceStatus,
        CatalogVisibilityScope visibilityScope,
        Set<Long> visibleDepartmentIds,
        Set<String> scenarios,
        Set<String> tags,
        Set<Long> relatedResourceIds,
        Set<Long> relatedSkillIds,
        boolean publish
) {
}
