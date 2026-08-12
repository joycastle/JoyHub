package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceSearchDocumentResponse;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative workflow for inspecting and re-queuing generated resource search profiles. */
@Service
public class ResourceSearchProfileAdminAppService {
    private final ResourceSearchDocumentJpaRepository repository;

    public ResourceSearchProfileAdminAppService(ResourceSearchDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceSearchDocumentResponse> list(String resourceType, String generationStatus,
                                                              int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        boolean hasType = resourceType != null && !resourceType.isBlank();
        boolean hasStatus = generationStatus != null && !generationStatus.isBlank();
        Page<ResourceSearchDocumentEntity> documents = hasType && hasStatus
                ? repository.findByResourceTypeAndGenerationStatus(normalize(resourceType), normalize(generationStatus), pageable)
                : hasType ? repository.findByResourceType(normalize(resourceType), pageable)
                : hasStatus ? repository.findByGenerationStatus(normalize(generationStatus), pageable)
                : repository.findAll(pageable);
        return PageResponse.from(documents.map(this::toResponse));
    }

    @Transactional
    public ResourceSearchDocumentResponse requestRegeneration(String resourceType, Long resourceId) {
        ResourceSearchDocumentEntity document = repository.findByResourceTypeAndResourceId(normalize(resourceType), resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource search document does not exist"));
        document.requestRegeneration();
        return toResponse(repository.save(document));
    }

    @Transactional(readOnly = true)
    public ResourceSearchDocumentResponse get(String resourceType, Long resourceId) {
        return repository.findByResourceTypeAndResourceId(normalize(resourceType), resourceId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Resource search document does not exist"));
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ResourceSearchDocumentResponse toResponse(ResourceSearchDocumentEntity document) {
        return new ResourceSearchDocumentResponse(document.getResourceType(), document.getResourceId(), document.getTitle(),
                document.getSlug(), document.getSummary(), document.getAccessMode(), document.isSearchEnabled(),
                document.getGenerationStatus(), document.getCompanyRelevance(), document.getCapabilitiesJson(),
                document.getScenariosJson(), document.getInputsJson(), document.getOutputsJson(),
                document.getSearchTermsJson(), document.getEvidenceJson(), document.getProfileText(),
                document.getRawDocumentation(), document.getSourceHash(), document.getGeneratedAt(), document.getUpdatedAt(),
                document.getCategoryCode().name(), document.getCategorySource().name());
    }
}
