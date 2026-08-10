package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.DiscoveryAssistResponse;
import com.iflytek.skillhub.dto.DiscoveryPlanStepResponse;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryAssistantAppService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryAssistantAppService.class);
    private static final int STEP_RESULT_LIMIT = 100;
    private static final int TOTAL_RESULT_LIMIT = 100;

    private final DiscoveryAiClient aiClient;
    private final DiscoveryAiProperties properties;
    private final DiscoveryKnowledgeRetriever knowledgeRetriever;
    private final DiscoveryConversationStore conversationStore;

    public DiscoveryAssistantAppService(DiscoveryAiClient aiClient,
                                        DiscoveryAiProperties properties,
                                        DiscoveryKnowledgeRetriever knowledgeRetriever,
                                        DiscoveryConversationStore conversationStore) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.knowledgeRetriever = knowledgeRetriever;
        this.conversationStore = conversationStore;
    }

    public DiscoveryAssistResponse assist(String question, String language, String conversationId,
                                          PlatformPrincipal principal,
                                          Map<Long, NamespaceRole> namespaceRoles) {
        Map<Long, NamespaceRole> normalizedRoles = namespaceRoles == null ? Map.of() : namespaceRoles;
        String safetyIdentifier = safetyIdentifier(principal.userId());
        DiscoveryConversationStore.Conversation conversation = conversationStore.load(
                principal.userId(), conversationId);
        DiscoverySearchPlan plan = plan(question, language, conversation.turns(), safetyIdentifier);
        List<DiscoveryPlanStepResponse> candidateSteps = retrieveSteps(
                plan, language, principal, normalizedRoles);
        DiscoveryAssistResponse response;

        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            List<DiscoverySuggestionResponse> suggestions = flatten(candidateSteps);
            response = localFallback(conversation.id(), language, candidateSteps, suggestions);
        } else {
            try {
                DiscoveryAiClient.AiAnswer answer = aiClient.answer(
                        question, language, candidateSteps, conversation.turns(), safetyIdentifier);
                List<DiscoveryPlanStepResponse> steps = applySelections(candidateSteps, answer.selections());
                List<DiscoverySuggestionResponse> suggestions = flatten(steps);
                response = new DiscoveryAssistResponse(
                        conversation.id(), answer.text(), suggestions, steps,
                        answer.model(), true, answer.fallbackUsed());
            } catch (RuntimeException exception) {
                log.warn("JoyHub AI assistant degraded to local guidance");
                List<DiscoverySuggestionResponse> suggestions = flatten(candidateSteps);
                response = localFallback(conversation.id(), language, candidateSteps, suggestions);
            }
        }
        conversationStore.append(principal.userId(), conversation.id(),
                new DiscoveryConversationTurn(question, response.answer()));
        return response;
    }

    private DiscoverySearchPlan plan(String question, String language,
                                     List<DiscoveryConversationTurn> history,
                                     String safetyIdentifier) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return DiscoverySearchPlan.singleStep(question);
        }
        try {
            return aiClient.plan(question, language, history, safetyIdentifier);
        } catch (RuntimeException exception) {
            log.warn("JoyHub goal planning degraded to a single retrieval step");
            return DiscoverySearchPlan.singleStep(question);
        }
    }

    private List<DiscoveryPlanStepResponse> retrieveSteps(DiscoverySearchPlan plan,
                                                          String language,
                                                          PlatformPrincipal principal,
                                                          Map<Long, NamespaceRole> namespaceRoles) {
        List<DiscoveryPlanStepResponse> steps = new ArrayList<>();
        for (DiscoverySearchPlan.Step step : plan.steps()) {
            List<DiscoverySuggestionResponse> matches = knowledgeRetriever
                    .retrieve(step.queries(), principal, namespaceRoles, language).stream()
                    .limit(STEP_RESULT_LIMIT)
                    .toList();
            steps.add(new DiscoveryPlanStepResponse(step.objective(), matches));
        }
        return List.copyOf(steps);
    }

    private List<DiscoverySuggestionResponse> flatten(List<DiscoveryPlanStepResponse> steps) {
        Map<String, DiscoverySuggestionResponse> unique = new LinkedHashMap<>();
        for (DiscoveryPlanStepResponse step : steps) {
            for (DiscoverySuggestionResponse suggestion : step.suggestions()) {
                unique.putIfAbsent(suggestion.type() + ":" + suggestion.id(), suggestion);
                if (unique.size() >= TOTAL_RESULT_LIMIT) {
                    return List.copyOf(unique.values());
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private List<DiscoveryPlanStepResponse> applySelections(
            List<DiscoveryPlanStepResponse> candidateSteps,
            List<DiscoveryAiClient.StepSelection> selections) {
        Map<Integer, List<DiscoveryAiClient.ResourceRef>> byStep = new LinkedHashMap<>();
        if (selections != null) {
            selections.forEach(selection -> byStep.put(selection.stepIndex(), selection.resources()));
        }
        List<DiscoveryPlanStepResponse> filtered = new ArrayList<>();
        for (int index = 0; index < candidateSteps.size(); index++) {
            DiscoveryPlanStepResponse step = candidateSteps.get(index);
            List<DiscoveryAiClient.ResourceRef> selected = byStep.getOrDefault(index, List.of());
            List<DiscoverySuggestionResponse> suggestions = selected.stream()
                    .map(reference -> step.suggestions().stream()
                            .filter(candidate -> reference.id().equals(candidate.id())
                                    && reference.type().equals(candidate.type()))
                            .findFirst()
                            .map(candidate -> withGuidance(candidate, reference))
                            .orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            filtered.add(new DiscoveryPlanStepResponse(step.objective(), suggestions));
        }
        return List.copyOf(filtered);
    }

    private DiscoverySuggestionResponse withGuidance(DiscoverySuggestionResponse candidate,
                                                       DiscoveryAiClient.ResourceRef reference) {
        String introduction = reference.introduction() == null || reference.introduction().isBlank()
                ? candidate.description() : reference.introduction();
        return new DiscoverySuggestionResponse(
                candidate.type(), candidate.id(), candidate.title(), introduction, candidate.kind(),
                candidate.slug(), candidate.namespace(), candidate.accessUrl(), reference.usage(),
                candidate.evidence(), candidate.source());
    }

    private DiscoveryAssistResponse localFallback(String conversationId, String language,
                                                  List<DiscoveryPlanStepResponse> steps,
                                                  List<DiscoverySuggestionResponse> suggestions) {
        boolean english = language != null && language.toLowerCase(Locale.ROOT).startsWith("en");
        String answer;
        if (suggestions.isEmpty()) {
            answer = english
                    ? "I broke the goal into actionable steps, but no directly reusable resource is visible yet."
                    : "我已把目标拆成可执行步骤，但暂时没有找到可直接复用且你有权限查看的资源。";
        } else {
            answer = english
                    ? "I broke the goal into " + steps.size() + " steps and matched visible resources to each step."
                    : "我已把目标拆成 " + steps.size() + " 个步骤，并为每一步匹配了你有权限查看的能力。";
        }
        return new DiscoveryAssistResponse(conversationId, answer, suggestions, steps, null, false, false);
    }

    private String safetyIdentifier(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            return "joyhub-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
