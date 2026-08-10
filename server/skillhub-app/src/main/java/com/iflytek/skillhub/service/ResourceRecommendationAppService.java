package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.RecommendedResourceResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces explainable homepage recommendations without relying on behavioral tracking. */
@Service
public class ResourceRecommendationAppService {
    private static final int CANDIDATE_MULTIPLIER = 4;

    private final UnifiedResourceSearchAppService searchAppService;
    private final NamespaceRepository namespaceRepository;

    public ResourceRecommendationAppService(UnifiedResourceSearchAppService searchAppService,
                                            NamespaceRepository namespaceRepository) {
        this.searchAppService = searchAppService;
        this.namespaceRepository = namespaceRepository;
    }

    @Transactional(readOnly = true)
    public List<RecommendedResourceResponse> recommend(int size, String userId,
                                                       Map<Long, NamespaceRole> namespaceRoles,
                                                       CatalogViewer catalogViewer) {
        int safeSize = Math.min(Math.max(size, 1), 24);
        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        Set<Long> departmentIds = roles.keySet();
        Set<String> departmentSlugs = namespaceRepository.findByIdIn(List.copyOf(departmentIds)).stream()
                .map(Namespace::getSlug)
                .collect(Collectors.toSet());
        PageResponse<UnifiedResourceSearchItemResponse> candidates = searchAppService.search(
                null, null, null, "newest", null, false, 0, safeSize * CANDIDATE_MULTIPLIER,
                userId, roles, catalogViewer);
        return candidates.items().stream()
                .sorted(Comparator.comparingInt((UnifiedResourceSearchItemResponse item) -> score(item, departmentIds, departmentSlugs)).reversed())
                .limit(safeSize)
                .map(item -> new RecommendedResourceResponse(item, reason(item, departmentIds, departmentSlugs)))
                .toList();
    }

    private int score(UnifiedResourceSearchItemResponse item, Set<Long> departmentIds, Set<String> departmentSlugs) {
        if (item.catalogResource() != null && item.catalogResource().department() != null
                && departmentIds.contains(item.catalogResource().department().id())) return 3;
        if (item.skill() != null && departmentSlugs.contains(item.skill().namespace())) return 2;
        return 1;
    }

    private String reason(UnifiedResourceSearchItemResponse item, Set<Long> departmentIds, Set<String> departmentSlugs) {
        if (item.catalogResource() != null && item.catalogResource().department() != null
                && departmentIds.contains(item.catalogResource().department().id())) return "适合你所在部门";
        if (item.skill() != null && departmentSlugs.contains(item.skill().namespace())) return "来自你所在部门";
        return "本周推荐";
    }
}
