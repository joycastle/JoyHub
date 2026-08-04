package com.iflytek.skillhub.catalog.domain;

import java.util.Set;

/** Transport-neutral command value consumed by the Catalog domain service. */
public record CatalogResourceDraft(
        String slug,
        String name,
        String summary,
        CatalogResourceKind kind,
        String icon,
        String accessUrl,
        String documentation,
        String version,
        Long primaryNamespaceId,
        CatalogMaintenanceStatus maintenanceStatus,
        CatalogVisibilityScope visibilityScope,
        Set<Long> visibleNamespaceIds,
        Set<String> scenarios,
        Set<String> tags,
        Set<Long> relatedResourceIds,
        Set<Long> relatedSkillIds
) {
    public CatalogResourceDraft {
        visibleNamespaceIds = immutable(visibleNamespaceIds);
        scenarios = immutable(scenarios);
        tags = immutable(tags);
        relatedResourceIds = immutable(relatedResourceIds);
        relatedSkillIds = immutable(relatedSkillIds);
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
