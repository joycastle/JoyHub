package com.iflytek.skillhub.dto;

import java.util.List;

public record DiscoveryAssistResponse(
        String conversationId,
        String answer,
        List<DiscoverySuggestionResponse> suggestions,
        List<DiscoveryPlanStepResponse> steps,
        String model,
        boolean modelGenerated,
        boolean fallbackUsed
) {
}
