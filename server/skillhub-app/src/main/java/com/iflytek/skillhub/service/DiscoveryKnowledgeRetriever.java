package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import com.iflytek.skillhub.search.ResourceSearchQueryInterpreter;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grounds the AI adviser exclusively in the same unified resource results shown to employees.
 * It does not chunk documents or perform a second RAG retrieval pass.
 */
@Service
public class DiscoveryKnowledgeRetriever {
    private static final int RESULT_LIMIT = 24;
    private final UnifiedResourceSearchAppService unifiedSearchAppService;
    private final ResourceSearchDocumentJpaRepository documentRepository;
    private final SearchTextTokenizer tokenizer;

    public DiscoveryKnowledgeRetriever(UnifiedResourceSearchAppService unifiedSearchAppService,
                                       ResourceSearchDocumentJpaRepository documentRepository,
                                       SearchTextTokenizer tokenizer) {
        this.unifiedSearchAppService = unifiedSearchAppService;
        this.documentRepository = documentRepository;
        this.tokenizer = tokenizer;
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(List<String> searchQueries, PlatformPrincipal principal,
                                                       Map<Long, NamespaceRole> namespaceRoles, String language) {
        return retrieve(searchQueries, principal, namespaceRoles, language,
                String.join(" ", searchQueries == null ? List.of() : searchQueries));
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(List<String> searchQueries, PlatformPrincipal principal,
                                                       Map<Long, NamespaceRole> namespaceRoles, String language,
                                                       String constraintSource) {
        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        CatalogViewer viewer = new CatalogViewer(principal.userId(), roles,
                principal.platformRoles() == null ? Set.of() : principal.platformRoles());
        Map<String, ResourceSearchDocumentEntity> documents = new LinkedHashMap<>();
        documentRepository.findBySearchEnabledTrue().forEach(document ->
                documents.put(document.getResourceType() + ":" + document.getResourceId(), document));
        Map<String, RankedSuggestion> suggestions = new LinkedHashMap<>();
        SearchConstraints constraints = SearchConstraints.from(constraintSource, tokenizer);
        List<String> queries = searchQueries == null ? List.of() : searchQueries;
        queries.stream().filter(query -> query != null && !query.isBlank()).map(String::trim).distinct()
                .limit(4).forEach(query -> {
                    unifiedSearchAppService.searchForDiscovery(query,
                        constraints.resourceType(), RESULT_LIMIT, principal.userId(), roles, viewer,
                        constraints.accessModes())
                        .items().stream()
                        .filter(item -> constraints.allowedTypes().isEmpty()
                                || constraints.allowedTypes().contains(item.resourceType()))
                        .forEach(item -> {
                            DiscoverySuggestionResponse suggestion = toSuggestion(query, item, documents, language);
                            if (suggestion != null) {
                                RankedSuggestion candidate = new RankedSuggestion(suggestion, item.relevanceScore());
                                suggestions.merge(suggestion.type() + ":" + suggestion.id(), candidate,
                                        (previous, next) -> next.score() > previous.score() ? next : previous);
                            }
                        });
                });
        return suggestions.values().stream().sorted(Comparator.comparingDouble(RankedSuggestion::score).reversed())
                .limit(RESULT_LIMIT).map(RankedSuggestion::suggestion).toList();
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(String question, PlatformPrincipal principal,
                                                       Map<Long, NamespaceRole> namespaceRoles) {
        return retrieve(List.of(question), principal, namespaceRoles, "zh-CN");
    }

    private DiscoverySuggestionResponse toSuggestion(String query, UnifiedResourceSearchItemResponse item,
                                                     Map<String, ResourceSearchDocumentEntity> documents,
                                                     String language) {
        if (item.skill() != null) {
            ResourceSearchDocumentEntity document = documents.get("SKILL:" + item.skill().id());
            String title = localized(language, item.skill().localizedDisplayName(), item.skill().displayName());
            String summary = localized(language, item.skill().localizedSummary(), item.skill().summary());
            return new DiscoverySuggestionResponse("skill", item.skill().id(), title, summary, "SKILL",
                    item.skill().slug(), item.skill().namespace(), null, null, evidence(query, document, summary),
                    "搜索画像");
        }
        if (item.catalogResource() != null) {
            ResourceSearchDocumentEntity document = documents.get(item.resourceType() + ":" + item.catalogResource().id());
            return new DiscoverySuggestionResponse("catalog", item.catalogResource().id(), item.catalogResource().name(),
                    item.catalogResource().summary(), item.catalogResource().kind(), item.catalogResource().slug(), null,
                    item.catalogResource().accessUrl(), null, evidence(query, document, item.catalogResource().summary()),
                    "搜索画像");
        }
        return null;
    }

    private String evidence(String query, ResourceSearchDocumentEntity document, String fallback) {
        if (document == null) return fallback;
        return bestExcerpt(query, String.join("\n\n", safe(document.getProfileText()),
                safe(document.getRawDocumentation())), fallback, tokenizer);
    }

    static String bestExcerpt(String question, String document, String fallback) {
        return bestExcerpt(question, document, fallback, new SearchTextTokenizer());
    }

    private static String bestExcerpt(String question, String document, String fallback, SearchTextTokenizer tokenizer) {
        if (document == null || document.isBlank()) return fallback;
        List<String> terms = tokenizer.tokenizeForQuery(question).stream().filter(term -> term.length() > 1).toList();
        String excerpt = java.util.Arrays.stream(document.split("(?:\\R\\s*){2,}|(?=#+\\s)"))
                .map(part -> part.replaceAll("\\s+", " ").trim()).filter(part -> !part.isBlank())
                .distinct()
                .sorted(Comparator.comparingLong((String part) -> terms.stream()
                        .filter(term -> part.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))).count()).reversed())
                .limit(3).map(part -> part.length() <= 360 ? part : part.substring(0, 357) + "…")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return excerpt.isBlank() ? fallback : excerpt;
    }

    private record RankedSuggestion(DiscoverySuggestionResponse suggestion, double score) { }

    private String localized(String language, String localized, String fallback) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en")
                ? safe(fallback).isBlank() ? safe(localized) : fallback
                : safe(localized).isBlank() ? safe(fallback) : localized;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record SearchConstraints(UnifiedResourceSearchType resourceType, Set<String> allowedTypes,
                                     Set<String> accessModes) {
        static SearchConstraints from(String query, SearchTextTokenizer tokenizer) {
            var intent = new ResourceSearchQueryInterpreter(tokenizer).interpret(query);
            UnifiedResourceSearchType type = intent.resourceTypes().size() == 1
                    ? UnifiedResourceSearchType.valueOf(intent.resourceTypes().iterator().next())
                    : UnifiedResourceSearchType.ALL;
            return new SearchConstraints(type, intent.resourceTypes(), intent.accessModes());
        }
    }
}
