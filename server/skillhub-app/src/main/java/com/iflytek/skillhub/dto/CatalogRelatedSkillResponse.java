package com.iflytek.skillhub.dto;

public record CatalogRelatedSkillResponse(
        Long id,
        String namespace,
        String slug,
        String name,
        String summary
) {
}
