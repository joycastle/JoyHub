package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.DiscoveryPlanStepResponse;
import java.util.List;

public interface DiscoveryAiClient {
    DiscoverySearchPlan plan(String question, String language, List<DiscoveryConversationTurn> history,
                             String safetyIdentifier);

    AiAnswer answer(String question, String language, List<DiscoveryPlanStepResponse> steps,
                    List<DiscoveryConversationTurn> history, String safetyIdentifier);

    LocalizedSkillMetadata localizeSkillMetadata(String name, String summary, String language,
                                                 String safetyIdentifier);

    record LocalizedSkillMetadata(String displayName, String summary) {
    }

    record AiAnswer(String text, String model, boolean fallbackUsed, List<StepSelection> selections) {
    }

    record StepSelection(int stepIndex, List<ResourceRef> resources) {
    }

    record ResourceRef(String type, Long id, String introduction, String usage) {
    }
}
