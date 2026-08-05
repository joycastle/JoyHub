package com.iflytek.skillhub.service;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.dto.AgentDocumentationDraftRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Generates a reviewable Agent usage-guide draft from publisher-supplied metadata. */
@Service
public class AgentDocumentationAiService {
    private final DiscoveryAiProperties properties;
    private final OpenAiResponsesClient aiClient;

    public AgentDocumentationAiService(DiscoveryAiProperties properties, OpenAiResponsesClient aiClient) {
        this.properties = properties;
        this.aiClient = aiClient;
    }

    public String draft(AgentDocumentationDraftRequest request, String userId, String language) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw CatalogDomainException.badRequest("error.catalog.ai.unavailable");
        }
        try {
            return aiClient.generateAgentDocumentation(
                    request.name().trim(), request.summary().trim(), request.scenarios(),
                    request.existingDocumentation(), language, safetyIdentifier(userId));
        } catch (RuntimeException exception) {
            throw CatalogDomainException.badRequest("error.catalog.ai.failed");
        }
    }

    private String safetyIdentifier(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            return "joyhub-agent-doc-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
