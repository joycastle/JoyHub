package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the aggregate-neutral search projection current without making publishing wait for AI. */
@Service
public class ResourceSearchDocumentSyncService {
    private final ResourceSearchDocumentJpaRepository documentRepository;
    private final SkillSearchDocumentJpaRepository legacySkillDocumentRepository;
    private final SkillRepository skillRepository;
    private final CatalogResourceRepository catalogResourceRepository;

    public ResourceSearchDocumentSyncService(ResourceSearchDocumentJpaRepository documentRepository,
                                             SkillSearchDocumentJpaRepository legacySkillDocumentRepository,
                                             SkillRepository skillRepository,
                                             CatalogResourceRepository catalogResourceRepository) {
        this.documentRepository = documentRepository;
        this.legacySkillDocumentRepository = legacySkillDocumentRepository;
        this.skillRepository = skillRepository;
        this.catalogResourceRepository = catalogResourceRepository;
    }

    @Scheduled(fixedDelayString = "${joyhub.search.document-sync-delay-ms:60000}")
    @Transactional
    public void synchronize() {
        skillRepository.findAll().forEach(this::synchronizeSkill);
        catalogResourceRepository.findAll().forEach(this::synchronizeCatalog);
    }

    @Transactional
    public void synchronizeSkill(Skill skill) {
        String rawDocumentation = legacySkillDocumentRepository.findBySkillId(skill.getId())
                .map(document -> document.getSearchText() == null ? "" : document.getSearchText())
                .orElse("");
        String title = preferred(skill.getLocalizedDisplayName(), preferred(skill.getDisplayName(), skill.getSlug()));
        String summary = preferred(skill.getLocalizedSummary(), skill.getSummary());
        boolean enabled = skill.getStatus() == SkillStatus.ACTIVE && !skill.isHidden();
        upsert("SKILL", skill.getId(), skill.getNamespaceId(), skill.getOwnerId(), title, skill.getSlug(), summary,
                "[]", "INSTALL", skill.getVisibility().name(), skill.getStatus().name(), rawDocumentation, enabled);
    }

    @Transactional
    public void synchronizeCatalog(CatalogResource resource) {
        String type = resource.getKind() == CatalogResourceKind.AGENT ? "AGENT" : "TOOL";
        String accessMode = resource.getAccessUrl() != null && !resource.getAccessUrl().isBlank()
                ? "OPEN" : resource.getArtifactStorageKey() != null ? "DOWNLOAD" : "OPEN";
        boolean enabled = resource.getStatus() == CatalogResourceStatus.PUBLISHED;
        upsert(type, resource.getId(), resource.getPrimaryNamespaceId(), resource.getOwnerId(), resource.getName(),
                resource.getSlug(), resource.getSummary(), toJson(resource.getScenarios()), accessMode,
                resource.getVisibilityScope().name(), resource.getStatus().name(), resource.getDocumentation(), enabled);
    }

    private void upsert(String type, Long resourceId, Long namespaceId, String ownerId, String title, String slug,
                        String summary, String scenariosJson, String accessMode, String visibility, String status,
                        String rawDocumentation, boolean enabled) {
        String documentation = rawDocumentation == null ? "" : rawDocumentation;
        String sourceHash = sha256(String.join("|", title, slug, summary == null ? "" : summary, documentation,
                scenariosJson, accessMode, visibility, status));
        ResourceSearchDocumentEntity document = documentRepository.findByResourceTypeAndResourceId(type, resourceId)
                .orElseGet(() -> ResourceSearchDocumentEntity.basic(type, resourceId, namespaceId, ownerId, title, slug,
                        summary, scenariosJson, accessMode, visibility, status, documentation, sourceHash));
        document.refreshBasic(namespaceId, ownerId, title, slug, summary, scenariosJson, accessMode, visibility,
                status, documentation, sourceHash, enabled);
        documentRepository.save(document);
    }

    private String toJson(java.util.Set<String> values) {
        return values == null || values.isEmpty() ? "[]" : values.stream().sorted()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String preferred(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback == null ? "" : fallback;
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte aByte : bytes) hex.append(String.format("%02x", aByte));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
