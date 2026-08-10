package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.CatalogQueryRepository;
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogResourceQueryAppService {
    private static final int HYBRID_CANDIDATE_LIMIT = 1000;

    private final CatalogQueryRepository queryRepository;
    private final CatalogResourceRepository resourceRepository;
    private final CatalogResourcePolicy policy;
    private final CatalogResourceProjectionAssembler assembler;
    private final HybridResourceSearchRanker searchRanker;

    public CatalogResourceQueryAppService(CatalogQueryRepository queryRepository,
                                          CatalogResourceRepository resourceRepository,
                                          CatalogResourcePolicy policy,
                                          CatalogResourceProjectionAssembler assembler,
                                          HybridResourceSearchRanker searchRanker) {
        this.queryRepository = queryRepository;
        this.resourceRepository = resourceRepository;
        this.policy = policy;
        this.assembler = assembler;
        this.searchRanker = searchRanker;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogResourceSummaryResponse> search(
            String query,
            String center,
            CatalogResourceKind kind,
            String scenario,
            Long departmentId,
            String sort,
            CatalogViewer viewer,
            Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return searchHybrid(query, center, kind, scenario, departmentId, sort, viewer, pageable);
        }
        Page<CatalogResource> page = queryRepository.searchPublished(
                null,
                center,
                kind,
                scenario,
                departmentId,
                sort,
                viewer.namespaceIds(),
                viewer.superAdmin(),
                pageable
        );
        List<CatalogResourceSummaryResponse> summaries = assembler.summaries(page.getContent());
        return PageResponse.from(new PageImpl<>(summaries, pageable, page.getTotalElements()));
    }

    private PageResponse<CatalogResourceSummaryResponse> searchHybrid(
            String query,
            String center,
            CatalogResourceKind kind,
            String scenario,
            Long departmentId,
            String sort,
            CatalogViewer viewer,
            Pageable pageable) {
        Page<CatalogResource> candidates = queryRepository.searchPublished(
                null, center, kind, scenario, departmentId, sort,
                viewer.namespaceIds(), viewer.superAdmin(),
                PageRequest.of(0, HYBRID_CANDIDATE_LIMIT));
        Map<String, CatalogResource> resourcesById = new LinkedHashMap<>();
        candidates.getContent().forEach(resource -> resourcesById.put(resource.getId().toString(), resource));
        List<ResourceSearchDocument> documents = candidates.getContent().stream()
                .map(this::toSearchDocument)
                .toList();
        List<HybridResourceSearchRanker.RankedResource> matches = kind == null
                ? searchRanker.rank(query, documents, HYBRID_CANDIDATE_LIMIT, false)
                : searchRanker.rankWithinScope(query, documents, HYBRID_CANDIDATE_LIMIT, false);
        List<CatalogResource> ranked = matches.stream()
                .map(match -> resourcesById.get(match.document().id()))
                .filter(java.util.Objects::nonNull)
                .toList();
        int from = Math.min((int) pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        List<CatalogResourceSummaryResponse> summaries = assembler.summaries(ranked.subList(from, to));
        return PageResponse.from(new PageImpl<>(summaries, pageable, ranked.size()));
    }

    private ResourceSearchDocument toSearchDocument(CatalogResource resource) {
        return new ResourceSearchDocument(
                resource.getId().toString(),
                resource.getKind() == CatalogResourceKind.AGENT ? "AGENT" : "TOOL",
                resource.getName(),
                resource.getSlug(),
                resource.getSummary(),
                List.copyOf(resource.getScenarios()),
                List.copyOf(resource.getTags()),
                resource.getDocumentation(),
                accessMode(resource),
                null,
                0D);
    }

    private String accessMode(CatalogResource resource) {
        if (resource.getAccessUrl() != null && !resource.getAccessUrl().isBlank()) {
            return "OPEN";
        }
        return resource.getArtifactStorageKey() != null ? "DOWNLOAD" : "OPEN";
    }

    @Transactional(readOnly = true)
    public CatalogResourceDetailResponse detail(String slug, CatalogViewer viewer) {
        CatalogResource resource = resourceRepository.findBySlug(slug)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", slug));
        if (!policy.canView(resource, viewer.userId(), viewer.namespaceIds(), viewer.superAdmin())) {
            throw CatalogDomainException.notFound("error.catalog.notFound", slug);
        }
        return assembler.detail(resource, viewer);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogResourceSummaryResponse> mine(CatalogViewer viewer, Pageable pageable) {
        List<CatalogResource> resources = resourceRepository.findByOwnerId(viewer.userId());
        int from = Math.min((int) pageable.getOffset(), resources.size());
        int to = Math.min(from + pageable.getPageSize(), resources.size());
        List<CatalogResourceSummaryResponse> content = assembler.summaries(resources.subList(from, to));
        return PageResponse.from(new PageImpl<>(content, PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize()), resources.size()));
    }
}
