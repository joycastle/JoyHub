package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchDocument;
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

    public UnifiedResourceSearchAppService(SkillSearchAppService skillSearchAppService,
                                           CatalogResourceQueryAppService catalogSearchAppService,
                                           ResourceFavoriteAppService favoriteAppService,
                                           HybridResourceSearchRanker searchRanker) {
        this.skillSearchAppService = skillSearchAppService;
        this.catalogSearchAppService = catalogSearchAppService;
        this.favoriteAppService = favoriteAppService;
        this.searchRanker = searchRanker;
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
        UnifiedResourceSearchType scope = type == null ? UnifiedResourceSearchType.ALL : type;
        List<Candidate> candidates = new ArrayList<>();

        if (scope == UnifiedResourceSearchType.ALL || scope == UnifiedResourceSearchType.SKILL) {
            List<String> labels = label == null || label.isBlank() ? List.of() : List.of(label);
            SkillSearchAppService.SearchResponse skills = skillSearchAppService.searchInstallableLatest(
                    null, namespace, "newest", 0, MAX_SKILL_CANDIDATES, labels, userId, namespaceRoles);
            skills.items().forEach(skill -> candidates.add(skillCandidate(skill)));
        }

        boolean skillSpecificFilter = (namespace != null && !namespace.isBlank())
                || (label != null && !label.isBlank());
        if (!skillSpecificFilter
                && catalogViewer != null
                && scope != UnifiedResourceSearchType.SKILL) {
            String center = scope == UnifiedResourceSearchType.AGENT
                    ? "AGENT"
                    : scope == UnifiedResourceSearchType.TOOL ? "TOOL" : null;
            PageResponse<CatalogResourceSummaryResponse> catalog = catalogSearchAppService.search(
                    null, center, null, null, null, null, catalogViewer,
                    PageRequest.of(0, MAX_CATALOG_CANDIDATES));
            catalog.items().forEach(resource -> candidates.add(catalogCandidate(resource)));
        }

        if (starredOnly) {
            Set<String> favorites = favoriteAppService.findFavoriteResourceIds(userId);
            candidates.removeIf(candidate -> !favorites.contains(favoriteKey(candidate)));
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

    private Candidate skillCandidate(SkillSummaryResponse skill) {
        String title = preferred(skill.localizedDisplayName(), skill.displayName());
        String summary = preferred(skill.localizedSummary(), skill.summary());
        ResourceSearchDocument document = new ResourceSearchDocument(
                skill.id().toString(), "SKILL", title, skill.slug(), summary,
                List.of(), List.of(), String.join("\n", safe(skill.displayName()), safe(skill.summary())),
                "INSTALL", null, quality(skill.downloadCount(), skill.starCount()));
        return new Candidate(document, skill, null);
    }

    private Candidate catalogCandidate(CatalogResourceSummaryResponse resource) {
        String resourceType = "AGENT".equals(resource.kind()) ? "AGENT" : "TOOL";
        String accessMode = resource.accessUrl() != null && !resource.accessUrl().isBlank()
                ? "OPEN"
                : resource.artifactAvailable() ? "DOWNLOAD" : "OPEN";
        ResourceSearchDocument document = new ResourceSearchDocument(
                resource.id().toString(), resourceType, resource.name(), resource.slug(), resource.summary(),
                resource.scenarios() == null ? List.of() : List.copyOf(resource.scenarios()),
                resource.tags() == null ? List.of() : List.copyOf(resource.tags()),
                "", accessMode, null, 0D);
        return new Candidate(document, null, resource);
    }

    private double quality(Long downloads, Integer stars) {
        long downloadCount = downloads == null ? 0L : downloads;
        int starCount = stars == null ? 0 : stars;
        return Math.min((Math.log1p(downloadCount) + Math.log1p(starCount)) / 10D, 1D);
    }

    private String key(ResourceSearchDocument document) {
        return document.resourceType() + ":" + document.id();
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
            CatalogResourceSummaryResponse catalogResource
    ) {
    }

    private record RankedCandidate(Candidate candidate, double relevanceScore) {
        private UnifiedResourceSearchItemResponse response() {
            return new UnifiedResourceSearchItemResponse(
                    candidate.document().resourceType(),
                    candidate.document().accessMode(),
                    relevanceScore,
                    candidate.skill(),
                    candidate.catalogResource());
        }
    }
}
