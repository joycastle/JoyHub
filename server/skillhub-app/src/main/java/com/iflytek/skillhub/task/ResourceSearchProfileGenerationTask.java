package com.iflytek.skillhub.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.service.OpenAiResponsesClient;
import com.iflytek.skillhub.service.ResourceSearchProfile;
import com.iflytek.skillhub.service.ResourceSearchProfileJobService;
import com.iflytek.skillhub.service.ResourceSearchProfileJobService.Job;
import com.iflytek.skillhub.service.ResourceSearchProfileJobService.GeneratedProfile;
import com.iflytek.skillhub.service.AiRelayException;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import java.util.List;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates bounded evidence-backed search profiles after publishing has completed. */
@Component
public class ResourceSearchProfileGenerationTask {
    private static final String PROMPT_VERSION = "resource-profile-v2-category";
    private final DiscoveryAiProperties properties;
    private final ResourceSearchProfileJobService jobs;
    private final OpenAiResponsesClient aiClient;
    private final ObjectMapper objectMapper;
    private final SearchEmbeddingService embeddingService;

    public ResourceSearchProfileGenerationTask(DiscoveryAiProperties properties,
                                               ResourceSearchProfileJobService jobs,
                                               OpenAiResponsesClient aiClient, ObjectMapper objectMapper,
                                               SearchEmbeddingService embeddingService) {
        this.properties = properties;
        this.jobs = jobs;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    @Scheduled(fixedDelayString = "${joyhub.search.profile-generation-delay-ms:15000}")
    public void generatePendingProfiles() {
        if (!properties.isEnabled()) return;
        for (Long id : jobs.candidates()) {
            if (Thread.currentThread().isInterrupted()) break;
            jobs.claim(id).ifPresent(job -> {
                try {
                    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                        throw new AiRelayException("AI_NOT_CONFIGURED", false);
                    }
                    generate(job);
                } catch (RuntimeException exception) {
                    jobs.fail(job, exception);
                }
            });
        }
    }

    private void generate(Job document) {
        ResourceSearchProfile generated = aiClient.generateSearchProfile(document.type(), document.title(),
                document.summary(), document.documentation(), "resource-search:" + document.type()
                        + ":" + document.resourceId());
        List<ResourceSearchProfile.Capability> verified = generated.capabilities().stream()
                .filter(capability -> capability.confidence() >= 0.70D)
                .filter(capability -> document.documentation().toLowerCase(Locale.ROOT)
                        .contains(capability.evidence().toLowerCase(Locale.ROOT)))
                .toList();
        try {
            String profileText = profileText(document, verified, generated);
            jobs.complete(document, new GeneratedProfile(objectMapper.writeValueAsString(verified),
                    objectMapper.writeValueAsString(generated.scenarios()), objectMapper.writeValueAsString(generated.inputs()),
                    objectMapper.writeValueAsString(generated.outputs()), objectMapper.writeValueAsString(generated.searchTerms()),
                    relevance(generated.companyRelevance()),
                    profileText, properties.getModel(), PROMPT_VERSION, embeddingService.embed(profileText),
                    generated.categoryCode() == null ? ResourceCategoryCode.OTHER : generated.categoryCode()));
        } catch (JsonProcessingException exception) {
            throw new AiRelayException("PROFILE_SERIALIZATION_FAILED", false);
        }
    }

    private String relevance(String value) {
        String normalized = value == null ? "GENERAL" : value.toUpperCase(Locale.ROOT);
        return List.of("CORE", "SUPPORTING", "GENERAL", "IRRELEVANT").contains(normalized) ? normalized : "GENERAL";
    }

    private String profileText(Job document, List<ResourceSearchProfile.Capability> capabilities,
                               ResourceSearchProfile generated) {
        return String.join("\n", document.title(), document.summary() == null ? "" : document.summary(),
                capabilities.stream().map(ResourceSearchProfile.Capability::value).collect(java.util.stream.Collectors.joining(" ")),
                String.join(" ", generated.scenarios()), String.join(" ", generated.outputs()),
                String.join(" ", generated.searchTerms()));
    }
}
