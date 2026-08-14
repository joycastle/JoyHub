package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A public Tool landing page used as evidence for an AI documentation draft. */
public record ToolDocumentationUrlDraftRequest(
        @NotBlank @Size(max = 2048) String accessUrl
) {
}
