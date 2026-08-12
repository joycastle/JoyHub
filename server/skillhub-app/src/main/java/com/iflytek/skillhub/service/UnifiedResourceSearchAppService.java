package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchDocument;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds one relevance-ranked result pool across every user-visible resource type. */
@Service
public class UnifiedResourceSearchAppService {
    private static final int MAX_SKILL_CANDIDATES = 500;
    private static final int MAX_CATALOG_CANDIDATES = 500;

    private final SkillSearchAppService skillSearchAppService;
    private final CatalogResourceQueryAppService catalogSearchAppService;
    private final ResourceFavoriteAppService favoriteAppService;
    private final HybridResourceSearchRanker searchRanker;
    private final ResourceSearchDocumentJpaRepository searchDocumentRepository;
    private final ObjectMapper objectMapper;

    public UnifiedResourceSearchAppService(SkillSearchAppService skillSearchAppService,
                                           CatalogResourceQueryAppService catalogSearchAppService,
                                           ResourceFavoriteAppService favoriteAppService,
                                           HybridResourceSearchRanker searchRanker,
                                           ResourceSearchDocumentJpaRepository searchDocumentRepository,
                                           ObjectMapper objectMapper) {
        this.skillSearchAppService = skillSearchAppService;
        this.catalogSearchAppService = catalogSearchAppService;
        this.favoriteAppService = favoriteAppService;
        this.searchRanker = searchRanker;
        this.searchDocumentRepository = searchDocumentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UnifiedResourceSearchItemResponse> search(
            String query,
            String namespace,
            String label,
            String sort,
            UnifiedResourceSearchType type,
            boolean starredOnly,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            CatalogViewer catalogViewer) {
        return search(query, namespace, label, sort, type, starredOnly, page, size, userId, namespaceRoles,
                catalogViewer, Set.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<UnifiedResourceSearchItemResponse> search(
            String query,
            String namespace,
            String label,
            String categoryCode,
            String sort,
            UnifiedResourceSearchType type,
            boolean starredOnly,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            CatalogViewer catalogViewer,
            Set<String> accessModes) {
        List<String> labels = label == null || label.isBlank() ? List.of() : List.of(label);
        return searchInternal(query, namespace, labels, categoryCode, sort, type, starredOnly, page, size,
                userId, namespaceRoles, catalogViewer, accessModes);
    }

    @Transactional(readOnly = true)
    public PageResponse<UnifiedResourceSearchItemResponse> search(
            String query,
            String namespace,
            String label,
            String sort,
            UnifiedResourceSearchType type,
            boolean starredOnly,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            CatalogViewer catalogViewer,
            Set<String> accessModes) {
        List<String> labels = label == null || label.isBlank() ? List.of() : List.of(label);
        return searchInternal(query, namespace, labels, null, sort, type, starredOnly, page, size, userId, namespaceRoles,
                catalogViewer, accessModes);
    }

    /**
     * Keeps the established Skill-only HTTP contract while routing its candidates and ranking
     * through the unified resource search pipeline.
     */
    @Transactional(readOnly = true)
    public SkillSearchAppService.SearchResponse searchSkills(
            String query,
            String namespace,
            List<String> labels,
            String sort,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles) {
        PageResponse<UnifiedResourceSearchItemResponse> response = searchInternal(
                query, namespace, labels == null ? List.of() : labels, null, sort, UnifiedResourceSearchType.SKILL,
                false, page, size, userId, namespaceRoles, null, Set.of());
        return new SkillSearchAppService.SearchResponse(response.items().stream()
                .map(UnifiedResourceSearchItemResponse::skill)
                .filter(java.util.Objects::nonNull)
                .toList(), response.total(), response.page(), response.size());
    }

    private PageResponse<UnifiedResourceSearchItemResponse> searchInternal(
            String query,
            String namespace,
            List<String> labels,
            String categoryCode,
            String sort,
            UnifiedResourceSearchType type,
            boolean starredOnly,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            CatalogViewer catalogViewer,
            Set<String> accessModes) {
        UnifiedResourceSearchType scope = type == null ? UnifiedResourceSearchType.ALL : type;
        List<String> normalizedLabels = labels == null ? List.of() : labels.stream()
                .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).toList();
        String normalizedCategory = normalizeCategory(categoryCode);
        List<Candidate> candidates = new ArrayList<>();
        Map<String, IndexedDocument> indexedDocuments = indexedDocuments();

        if (scope == UnifiedResourceSearchType.ALL || scope == UnifiedResourceSearchType.SKILL) {
            SkillSearchAppService.SearchResponse skills = skillSearchAppService.searchInstallableLatest(
                    null, namespace, "newest", 0, MAX_SKILL_CANDIDATES, normalizedLabels, userId, namespaceRoles);
            skills.items().forEach(skill -> candidates.add(skillCandidate(skill, indexedDocuments)));
        }

        boolean skillSpecificFilter = (namespace != null && !namespace.isBlank())
                || !normalizedLabels.isEmpty();
        if (!skillSpecificFilter
                && catalogViewer != null
                && scope != UnifiedResourceSearchType.SKILL) {
            String center = scope == UnifiedResourceSearchType.AGENT
                    ? "AGENT"
                    : scope == UnifiedResourceSearchType.TOOL ? "TOOL" : null;
            PageResponse<CatalogResourceSummaryResponse> catalog = catalogSearchAppService.search(
                    null, center, null, null, null, null, catalogViewer,
                    PageRequest.of(0, MAX_CATALOG_CANDIDATES));
            catalog.items().forEach(resource -> candidates.add(catalogCandidate(resource, indexedDocuments)));
        }

        if (normalizedCategory != null) {
            candidates.removeIf(candidate -> !normalizedCategory.equals(candidate.categoryCode()));
        }

        if (starredOnly) {
            Set<String> favorites = favoriteAppService.findFavoriteResourceIds(userId);
            candidates.removeIf(candidate -> !favorites.contains(favoriteKey(candidate)));
        }
        if (accessModes != null && !accessModes.isEmpty()) {
            Set<String> normalizedAccessModes = accessModes.stream().filter(java.util.Objects::nonNull)
                    .map(value -> value.trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            candidates.removeIf(candidate -> !normalizedAccessModes.contains(candidate.document().accessMode()));
        }

        List<RankedCandidate> ranked = new ArrayList<>(rank(query, scope, candidates));
        sort(sort, ranked);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = (int) Math.min((long) safePage * safeSize, ranked.size());
        int to = Math.min(from + safeSize, ranked.size());
        List<UnifiedResourceSearchItemResponse> items = ranked.subList(from, to).stream()
                .map(RankedCandidate::response)
                .toList();
        return new PageResponse<>(items, ranked.size(), safePage, safeSize);
    }

    private List<RankedCandidate> rank(String query,
                                       UnifiedResourceSearchType scope,
                                       List<Candidate> candidates) {
        if (query == null || query.isBlank()) {
            return candidates.stream()
                    .map(candidate -> new RankedCandidate(candidate, 0D))
                    .toList();
        }
        Map<String, Candidate> candidatesByKey = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesByKey.put(key(candidate.document()), candidate));
        List<HybridResourceSearchRanker.RankedResource> matches = scope == UnifiedResourceSearchType.ALL
                ? searchRanker.rank(query, candidates.stream().map(Candidate::document).toList(), candidates.size(), false)
                : searchRanker.rankWithinScope(
                        query, candidates.stream().map(Candidate::document).toList(), candidates.size(), false);
        return matches.stream()
                .map(match -> new RankedCandidate(candidatesByKey.get(key(match.document())), match.score()))
                .filter(result -> result.candidate() != null)
                .toList();
    }

    private void sort(String sort, List<RankedCandidate> ranked) {
        String normalizedSort = sort == null ? "relevance" : sort.trim().toLowerCase(Locale.ROOT);
        if ("downloads".equals(normalizedSort)) {
            ranked.sort(Comparator.comparingLong(this::downloadCount).reversed()
                    .thenComparing(this::updatedAt, Comparator.reverseOrder()));
        } else if ("newest".equals(normalizedSort)) {
            ranked.sort(Comparator.comparing(this::updatedAt).reversed());
        }
    }

    private long downloadCount(RankedCandidate ranked) {
        SkillSummaryResponse skill = ranked.candidate().skill();
        return skill != null && skill.downloadCount() != null ? skill.downloadCount() : 0L;
    }

    private Instant updatedAt(RankedCandidate ranked) {
        Candidate candidate = ranked.candidate();
        Instant updatedAt = candidate.skill() != null
                ? candidate.skill().updatedAt()
                : candidate.catalogResource().updatedAt();
        return updatedAt != null ? updatedAt : Instant.EPOCH;
    }

    private Candidate skillCandidate(SkillSummaryResponse skill,
                                     Map<String, IndexedDocument> indexedDocuments) {
        IndexedDocument indexed = indexedDocuments.get("SKILL:" + skill.id());
        if (indexed != null) {
            return new Candidate(indexed.document(), skill, null, indexed.categoryCode());
        }
        String title = preferred(skill.localizedDisplayName(), skill.displayName());
        String summary = preferred(skill.localizedSummary(), skill.summary());
        ResourceSearchDocument document = new ResourceSearchDocument(
                skill.id().toString(), "SKILL", title, skill.slug(), summary,
                List.of(), List.of(), String.join("\n", safe(skill.displayName()), safe(skill.summary())),
                "INSTALL", null, quality(skill.downloadCount(), skill.starCount()));
        return new Candidate(document, skill, null, ResourceCategoryCode.OTHER.name());
    }

    private Candidate catalogCandidate(CatalogResourceSummaryResponse resource,
                                       Map<String, IndexedDocument> indexedDocuments) {
        String resourceType = "AGENT".equals(resource.kind()) ? "AGENT" : "TOOL";
        IndexedDocument indexed = indexedDocuments.get(resourceType + ":" + resource.id());
        if (indexed != null) {
            return new Candidate(indexed.document(), null, resource, indexed.categoryCode());
        }
        String accessMode = resource.accessUrl() != null && !resource.accessUrl().isBlank()
                ? "OPEN"
                : resource.artifactAvailable() ? "DOWNLOAD" : "OPEN";
        ResourceSearchDocument document = new ResourceSearchDocument(
                resource.id().toString(), resourceType, resource.name(), resource.slug(), resource.summary(),
                resource.scenarios() == null ? List.of() : List.copyOf(resource.scenarios()),
                resource.tags() == null ? List.of() : List.copyOf(resource.tags()),
                "", accessMode, null, 0D);
        return new Candidate(document, null, resource, ResourceCategoryCode.OTHER.name());
    }

    private double quality(Long downloads, Integer stars) {
        long downloadCount = downloads == null ? 0L : downloads;
        int starCount = stars == null ? 0 : stars;
        return Math.min((Math.log1p(downloadCount) + Math.log1p(starCount)) / 10D, 1D);
    }

    private String key(ResourceSearchDocument document) {
        return document.resourceType() + ":" + document.id();
    }

    /**
     * The projection is the sole ranking input.  The temporary per-resource fallback keeps a
     * freshly published resource discoverable while its committed index event is still queued.
     */
    private Map<String, IndexedDocument> indexedDocuments() {
        Map<String, IndexedDocument> result = new LinkedHashMap<>();
        for (ResourceSearchDocumentEntity entity : searchDocumentRepository.findBySearchEnabledTrue()) {
            ResourceSearchDocument document = new ResourceSearchDocument(
                    entity.getResourceId().toString(), entity.getResourceType(), entity.getTitle(), entity.getSlug(),
                    entity.getSummary(), merge(jsonList(entity.getScenariosJson()), jsonList(entity.getInputsJson()),
                    jsonList(entity.getOutputsJson())), merge(jsonList(entity.getCapabilitiesJson()),
                    jsonList(entity.getSearchTermsJson())),
                    entity.getProfileText() + "\n" + entity.getRawDocumentation(), entity.getAccessMode(),
                    entity.getSemanticVector(), 0D);
            result.put(key(document), new IndexedDocument(document,
                    entity.getCategoryCode() == null ? ResourceCategoryCode.OTHER.name()
                            : entity.getCategoryCode().name()));
        }
        return result;
    }

    private String normalizeCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        try {
            return ResourceCategoryCode.valueOf(categoryCode.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.resource.category.invalid", categoryCode);
        }
    }

    private List<String> jsonList(String value) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "[]" : value,
                    new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).filter(value -> value != null && !value.isBlank())
                .distinct().toList();
    }

    private String favoriteKey(Candidate candidate) {
        return candidate.skill() != null
                ? "SKILL:" + candidate.skill().id()
                : "CATALOG:" + candidate.catalogResource().id();
    }

    private String preferred(String localized, String fallback) {
        return localized != null && !localized.isBlank() ? localized : safe(fallback);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record Candidate(
            ResourceSearchDocument document,
            SkillSummaryResponse skill,
            CatalogResourceSummaryResponse catalogResource,
            String categoryCode
    ) {
    }

    private record RankedCandidate(Candidate candidate, double relevanceScore) {
        private UnifiedResourceSearchItemResponse response() {
            return new UnifiedResourceSearchItemResponse(
                    candidate.document().resourceType(),
                    candidate.document().accessMode(),
                    relevanceScore,
                    candidate.skill(),
                    candidate.catalogResource(),
                    candidate.categoryCode());
        }
    }

    private record IndexedDocument(ResourceSearchDocument document, String categoryCode) { }
}
