package com.iflytek.skillhub.dto;

import java.time.Instant;

/** Administrator view of the generated, aggregate-neutral resource search profile. */
public record ResourceSearchDocumentResponse(
        String resourceType,
        Long resourceId,
        String title,
        String slug,
        String summary,
        String accessMode,
        boolean searchEnabled,
        String generationStatus,
        String companyRelevance,
        String capabilitiesJson,
        String scenariosJson,
        String inputsJson,
        String outputsJson,
        String searchTermsJson,
        String evidenceJson,
        String profileText,
        String rawDocumentation,
        String sourceHash,
        Instant generatedAt,
        Instant updatedAt,
        String categoryCode,
        String categorySource
) { }
