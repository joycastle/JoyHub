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
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogResourceQueryAppService {
    private final CatalogQueryRepository queryRepository;
    private final CatalogResourceRepository resourceRepository;
    private final CatalogResourcePolicy policy;
    private final CatalogResourceProjectionAssembler assembler;

    public CatalogResourceQueryAppService(CatalogQueryRepository queryRepository,
                                          CatalogResourceRepository resourceRepository,
                                          CatalogResourcePolicy policy,
                                          CatalogResourceProjectionAssembler assembler) {
        this.queryRepository = queryRepository;
        this.resourceRepository = resourceRepository;
        this.policy = policy;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogResourceSummaryResponse> search(
            String query,
            String center,
            CatalogResourceKind kind,
            String scenario,
            Long departmentId,
            CatalogViewer viewer,
            Pageable pageable) {
        Page<CatalogResource> page = queryRepository.searchPublished(
                query,
                center,
                kind,
                scenario,
                departmentId,
                viewer.namespaceIds(),
                viewer.superAdmin(),
                pageable
        );
        List<CatalogResourceSummaryResponse> summaries = assembler.summaries(page.getContent());
        return PageResponse.from(new PageImpl<>(summaries, pageable, page.getTotalElements()));
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
