package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AgentDocumentationDraftRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 1200) String summary,
        List<@Size(max = 96) String> scenarios,
        @Size(max = 12000) String existingDocumentation
) {
}
