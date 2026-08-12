package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.ResourceCategoryResponse;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assigns the one shared category field without coupling Skill or Catalog aggregates. */
@Service
public class ResourceCategoryAppService {
    private final ResourceSearchDocumentJpaRepository documentRepository;
    private final ResourceSearchDocumentSyncService syncService;
    private final SkillRepository skillRepository;
    private final CatalogResourceRepository catalogResourceRepository;

    public ResourceCategoryAppService(ResourceSearchDocumentJpaRepository documentRepository,
                                      ResourceSearchDocumentSyncService syncService,
                                      SkillRepository skillRepository,
                                      CatalogResourceRepository catalogResourceRepository) {
        this.documentRepository = documentRepository;
        this.syncService = syncService;
        this.skillRepository = skillRepository;
        this.catalogResourceRepository = catalogResourceRepository;
    }

    /** Validates an author supplied value before a publish/create command mutates its aggregate. */
    public void validateRequestedCategory(String rawCategoryCode) {
        if (rawCategoryCode == null || rawCategoryCode.isBlank()) {
            return;
        }
        parseAuthorCategory(rawCategoryCode);
    }

    @Transactional
    public ResourceCategoryResponse update(String resourceType, Long resourceId, String rawCategoryCode,
                                           CatalogViewer viewer) {
        if (viewer == null || viewer.userId() == null || viewer.userId().isBlank()) {
            throw new DomainForbiddenException("error.auth.required");
        }
        String normalizedType = normalizeType(resourceType);
        String ownerId;
        if ("SKILL".equals(normalizedType)) {
            Skill skill = skillRepository.findById(resourceId)
                    .orElseThrow(() -> new DomainNotFoundException("error.resource.notFound", resourceType, resourceId));
            ownerId = skill.getOwnerId();
            if (!viewer.superAdmin() && !viewer.userId().equals(ownerId)) {
                throw new DomainForbiddenException("error.resource.category.forbidden");
            }
            syncService.synchronizeSkill(skill);
        } else {
            CatalogResource resource = catalogResourceRepository.findById(resourceId)
                    .orElseThrow(() -> new DomainNotFoundException("error.resource.notFound", resourceType, resourceId));
            String expectedType = resource.getKind() == CatalogResourceKind.AGENT ? "AGENT" : "TOOL";
            if (!expectedType.equals(normalizedType)) {
                throw new DomainNotFoundException("error.resource.notFound", resourceType, resourceId);
            }
            ownerId = resource.getOwnerId();
            if (!viewer.superAdmin() && !viewer.userId().equals(ownerId)) {
                throw new DomainForbiddenException("error.resource.category.forbidden");
            }
            syncService.synchronizeCatalog(resource);
        }

        ResourceSearchDocumentEntity document = documentRepository
                .findByResourceTypeAndResourceId(normalizedType, resourceId)
                .orElseThrow(() -> new DomainNotFoundException("error.resource.notFound", resourceType, resourceId));
        if (rawCategoryCode == null || rawCategoryCode.isBlank()) {
            document.useAiCategory();
        } else {
            document.setAuthorCategory(parseAuthorCategory(rawCategoryCode));
        }
        ResourceSearchDocumentEntity saved = documentRepository.save(document);
        return toResponse(saved);
    }

    private ResourceCategoryCode parseAuthorCategory(String rawCategoryCode) {
        try {
            return ResourceCategoryCode.valueOf(rawCategoryCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new DomainBadRequestException("error.resource.category.invalid", rawCategoryCode);
        }
    }

    private String normalizeType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT);
        if (!"SKILL".equals(normalized) && !"AGENT".equals(normalized) && !"TOOL".equals(normalized)) {
            throw new DomainBadRequestException("error.resource.category.type.invalid", resourceType);
        }
        return normalized;
    }

    private ResourceCategoryResponse toResponse(ResourceSearchDocumentEntity document) {
        return new ResourceCategoryResponse(document.getResourceType(), document.getResourceId(),
                document.getCategoryCode().name(), document.getCategorySource().name());
    }
}
