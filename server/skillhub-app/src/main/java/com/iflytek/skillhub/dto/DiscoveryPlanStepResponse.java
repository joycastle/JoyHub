package com.iflytek.skillhub.dto;

import java.util.List;

public record DiscoveryPlanStepResponse(
        String objective,
        List<DiscoverySuggestionResponse> suggestions
) {
}
