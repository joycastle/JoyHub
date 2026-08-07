package com.iflytek.skillhub.dto;

/** Reviewable metadata and usage-guide draft inferred from an uploaded archive. */
public record ArchiveDocumentationDraftResponse(
        String summary,
        String documentation
) {
}
