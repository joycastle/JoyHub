package com.iflytek.skillhub.dto;

/**
 * One ranked result from the shared Skill, Agent, and Tool candidate pool.
 * Exactly one of {@code skill} and {@code catalogResource} is populated.
 */
public record UnifiedResourceSearchItemResponse(
        String resourceType,
        String accessMode,
        double relevanceScore,
        SkillSummaryResponse skill,
        CatalogResourceSummaryResponse catalogResource,
        String categoryCode
) {
    public UnifiedResourceSearchItemResponse(String resourceType,
                                             String accessMode,
                                             double relevanceScore,
                                             SkillSummaryResponse skill,
                                             CatalogResourceSummaryResponse catalogResource) {
        this(resourceType, accessMode, relevanceScore, skill, catalogResource, "OTHER");
    }
}
