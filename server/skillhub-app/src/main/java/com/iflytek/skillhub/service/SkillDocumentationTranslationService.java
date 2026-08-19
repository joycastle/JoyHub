package com.iflytek.skillhub.service;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslation;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService.ReadableSkillFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Translates Skill documentation once and reuses the persisted Chinese (or requested-language) copy. */
@Service
public class SkillDocumentationTranslationService {
    private static final Logger log = LoggerFactory.getLogger(SkillDocumentationTranslationService.class);
    public static final String CHINESE = "zh-CN";
    private static final int MAX_DOCUMENTATION_BYTES = 160_000;

    private final DiscoveryAiProperties properties;
    private final OpenAiResponsesClient aiClient;
    private final SkillQueryService skillQueryService;
    private final SkillFileRepository skillFileRepository;
    private final SkillDocumentationTranslationRepository translationRepository;

    public SkillDocumentationTranslationService(DiscoveryAiProperties properties,
                                                OpenAiResponsesClient aiClient,
                                                SkillQueryService skillQueryService,
                                                SkillFileRepository skillFileRepository,
                                                SkillDocumentationTranslationRepository translationRepository) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.skillQueryService = skillQueryService;
        this.skillFileRepository = skillFileRepository;
        this.translationRepository = translationRepository;
    }

    @Transactional
    public String translate(String namespace, String slug, String version, String path,
                            String language, String userId, Map<Long, NamespaceRole> namespaceRoles) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.pathRequired");
        }
        ReadableSkillFile readable = skillQueryService.resolveReadableFile(
                namespace, slug, version, path, userId,
                namespaceRoles == null ? Map.of() : namespaceRoles);
        return translationFor(readable.skillId(), readable.versionId(), readable.file(),
                normalizeLanguage(language), userId);
    }

    @Transactional
    public void warmChineseDocumentation(long skillId, long versionId, String actorId) {
        if (!aiEnabled()) {
            return;
        }
        List<SkillFile> files = skillFileRepository.findByVersionId(versionId);
        String path = resolveDocumentationPath(files);
        if (path == null) {
            return;
        }
        try {
            SkillFile file = files.stream()
                    .filter(candidate -> path.equals(candidate.getFilePath()))
                    .findFirst()
                    .orElse(null);
            if (file == null) {
                return;
            }
            translationFor(skillId, versionId, file, CHINESE, actorId);
        } catch (RuntimeException exception) {
            log.warn("Could not cache Chinese documentation [skillId={}, versionId={}]: {}",
                    skillId, versionId, exception.getMessage());
        }
    }

    private String translationFor(long skillId, long versionId, SkillFile file, String language, String userId) {
        String fileSha = file.getSha256();
        if (fileSha != null && !fileSha.isBlank()) {
            var cached = translationRepository.findByVersionIdAndPathAndLanguage(
                    versionId, file.getFilePath(), language);
            if (cached.isPresent() && fileSha.equals(cached.get().getSourceSha256())) {
                return cached.get().getMarkdown();
            }
        }
        String markdown = readMarkdown(file);
        if (markdown.isBlank()) {
            return markdown;
        }
        return generateAndStore(skillId, versionId, file.getFilePath(), language,
                sourceSha256(file, markdown), markdown, userId);
    }

    private String generateAndStore(long skillId, long versionId, String path, String language,
                                    String sourceSha, String markdown, String userId) {
        if (!aiEnabled()) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.unavailable");
        }
        String translated = aiClient.translateMarkdown(markdown, language, safetyIdentifier(userId));
        SkillDocumentationTranslation stored = translationRepository
                .findByVersionIdAndPathAndLanguage(versionId, path, language)
                .orElseGet(() -> new SkillDocumentationTranslation(
                        skillId, versionId, path, language, sourceSha, translated, translationModelName()));
        stored.replace(sourceSha, translated, translationModelName());
        translationRepository.save(stored);
        return translated;
    }

    private String readMarkdown(SkillFile file) {
        try (InputStream content = skillQueryService.getFileContentByVersionId(file.getVersionId(), file.getFilePath())) {
            byte[] bytes = content.readNBytes(MAX_DOCUMENTATION_BYTES + 1);
            if (bytes.length > MAX_DOCUMENTATION_BYTES) {
                throw new DomainBadRequestException(
                        "error.skill.documentationTranslation.tooLarge", MAX_DOCUMENTATION_BYTES);
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (LocalizedDomainException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DomainBadRequestException("error.skill.documentationTranslation.failed");
        }
    }

    private boolean aiEnabled() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private String translationModelName() {
        return properties.getTranslationModel() == null || properties.getTranslationModel().isBlank()
                ? properties.getModel() : properties.getTranslationModel();
    }

    private String normalizeLanguage(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en") ? "en-US" : CHINESE;
    }

    private String sourceSha256(SkillFile file, String markdown) {
        if (file.getSha256() != null && !file.getSha256().isBlank()) {
            return file.getSha256();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(markdown.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String resolveDocumentationPath(List<SkillFile> files) {
        List<String> paths = files.stream().map(SkillFile::getFilePath).toList();
        if (paths.contains("README.md")) {
            return "README.md";
        }
        if (paths.contains("SKILL.md")) {
            return "SKILL.md";
        }
        return null;
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
