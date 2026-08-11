package com.iflytek.skillhub.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.service.OpenAiResponsesClient;
import com.iflytek.skillhub.service.ResourceSearchProfile;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Generates bounded evidence-backed search profiles after publishing has completed. */
@Component
public class ResourceSearchProfileGenerationTask {
    private static final String PROMPT_VERSION = "resource-profile-v1";
    private static final Logger log = LoggerFactory.getLogger(ResourceSearchProfileGenerationTask.class);
    private final DiscoveryAiProperties properties;
    private final ResourceSearchDocumentJpaRepository repository;
    private final OpenAiResponsesClient aiClient;
    private final ObjectMapper objectMapper;
    private final SearchEmbeddingService embeddingService;

    public ResourceSearchProfileGenerationTask(DiscoveryAiProperties properties,
                                               ResourceSearchDocumentJpaRepository repository,
                                               OpenAiResponsesClient aiClient, ObjectMapper objectMapper,
                                               SearchEmbeddingService embeddingService) {
        this.properties = properties;
        this.repository = repository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    @Scheduled(fixedDelayString = "${joyhub.search.profile-generation-delay-ms:15000}")
    @Transactional
    public void generatePendingProfiles() {
        if (!properties.isEnabled()) return;
        repository.findTop20ByGenerationStatusOrderByUpdatedAtAsc("PENDING").forEach(document -> {
            try {
                generate(document);
            } catch (RuntimeException exception) {
                document.markGenerationFailed();
                repository.save(document);
                log.warn("Search profile generation failed [type={}, resourceId={}]",
                        document.getResourceType(), document.getResourceId());
            }
        });
    }

    private void generate(ResourceSearchDocumentEntity document) {
        ResourceSearchProfile generated = aiClient.generateSearchProfile(document.getResourceType(), document.getTitle(),
                document.getSummary(), document.getRawDocumentation(), "resource-search:" + document.getResourceType()
                        + ":" + document.getResourceId());
        List<ResourceSearchProfile.Capability> verified = generated.capabilities().stream()
                .filter(capability -> capability.confidence() >= 0.70D)
                .filter(capability -> document.getRawDocumentation().toLowerCase(Locale.ROOT)
                        .contains(capability.evidence().toLowerCase(Locale.ROOT)))
                .toList();
        try {
            String profileText = profileText(document, verified, generated);
            document.applyGeneratedProfile(objectMapper.writeValueAsString(verified),
                    objectMapper.writeValueAsString(generated.scenarios()), objectMapper.writeValueAsString(generated.inputs()),
                    objectMapper.writeValueAsString(generated.outputs()), objectMapper.writeValueAsString(generated.searchTerms()),
                    objectMapper.writeValueAsString(verified), relevance(generated.companyRelevance()),
                    profileText, properties.getModel(), PROMPT_VERSION, embeddingService.embed(profileText));
            repository.save(document);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize generated resource profile", exception);
        }
    }

    private String relevance(String value) {
        String normalized = value == null ? "GENERAL" : value.toUpperCase(Locale.ROOT);
        return List.of("CORE", "SUPPORTING", "GENERAL", "IRRELEVANT").contains(normalized) ? normalized : "GENERAL";
    }

    private String profileText(ResourceSearchDocumentEntity document, List<ResourceSearchProfile.Capability> capabilities,
                               ResourceSearchProfile generated) {
        return String.join("\n", document.getTitle(), document.getSummary() == null ? "" : document.getSummary(),
                capabilities.stream().map(ResourceSearchProfile.Capability::value).collect(java.util.stream.Collectors.joining(" ")),
                String.join(" ", generated.scenarios()), String.join(" ", generated.outputs()),
                String.join(" ", generated.searchTerms()));
    }
}
