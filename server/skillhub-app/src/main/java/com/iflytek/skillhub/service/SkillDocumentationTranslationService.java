package com.iflytek.skillhub.service;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Translates the selected Skill documentation on demand without changing the published package. */
@Service
public class SkillDocumentationTranslationService {
    private static final int MAX_DOCUMENTATION_BYTES = 160_000;

    private final DiscoveryAiProperties properties;
    private final OpenAiResponsesClient aiClient;
    private final SkillQueryService skillQueryService;

    public SkillDocumentationTranslationService(DiscoveryAiProperties properties,
                                                OpenAiResponsesClient aiClient,
                                                SkillQueryService skillQueryService) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.skillQueryService = skillQueryService;
    }

    public String translate(String namespace, String slug, String version, String path,
                            String language, String userId, Map<Long, NamespaceRole> namespaceRoles) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.unavailable");
        }
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.pathRequired");
        }

        try (InputStream content = skillQueryService.getFileContent(
                namespace, slug, version, path, userId,
                namespaceRoles == null ? Map.of() : namespaceRoles)) {
            byte[] bytes = content.readNBytes(MAX_DOCUMENTATION_BYTES + 1);
            if (bytes.length > MAX_DOCUMENTATION_BYTES) {
                throw new DomainBadRequestException(
                        "error.skill.documentationTranslation.tooLarge", MAX_DOCUMENTATION_BYTES);
            }
            String markdown = new String(bytes, StandardCharsets.UTF_8).trim();
            if (markdown.isBlank()) {
                return markdown;
            }
            return aiClient.translateMarkdown(markdown, normalizeLanguage(language), safetyIdentifier(userId));
        } catch (LocalizedDomainException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.failed");
        }
    }

    private String normalizeLanguage(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en") ? "en-US" : "zh-CN";
    }

    private String safetyIdentifier(String userId) {
        String subject = userId == null || userId.isBlank() ? "anonymous" : userId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return "joyhub-skill-translation-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
