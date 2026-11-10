package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.util.LinkedHashMap;
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
        Map<String, DiscoverySuggestionResponse> suggestions = new LinkedHashMap<>();
        SearchConstraints constraints = SearchConstraints.from(constraintSource);
        List<String> queries = searchQueries == null ? List.of() : searchQueries;
        queries.stream().filter(query -> query != null && !query.isBlank()).map(String::trim).distinct()
                .limit(4).forEach(query -> {
                    unifiedSearchAppService.search(query, null, null, "relevance",
                        constraints.resourceType(), false, 0, RESULT_LIMIT, principal.userId(), roles, viewer,
                        constraints.accessModes())
                        .items().forEach(item -> {
                            DiscoverySuggestionResponse suggestion = toSuggestion(query, item, documents, language);
                            if (suggestion != null) suggestions.putIfAbsent(suggestion.type() + ":" + suggestion.id(), suggestion);
                        });
                });
        return suggestions.values().stream().limit(RESULT_LIMIT).toList();
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
        return bestExcerpt(query, String.join("\n", safe(document.getEvidenceJson()), safe(document.getProfileText())), fallback);
    }

    static String bestExcerpt(String question, String document, String fallback) {
        if (document == null || document.isBlank()) return fallback;
        List<String> terms = new SearchTextTokenizer().tokenizeForQuery(question);
        return java.util.Arrays.stream(document.split("(?:\\R\\s*){2,}|(?=#+\\s)"))
                .map(part -> part.replaceAll("\\s+", " ").trim()).filter(part -> !part.isBlank())
                .max(java.util.Comparator.comparingLong(part -> terms.stream()
                        .filter(term -> part.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))).count()))
                .map(part -> part.length() <= 220 ? part : part.substring(0, 217) + "…").orElse(fallback);
    }

    private String localized(String language, String localized, String fallback) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en")
                ? safe(fallback).isBlank() ? safe(localized) : fallback
                : safe(localized).isBlank() ? safe(fallback) : localized;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record SearchConstraints(UnifiedResourceSearchType resourceType, Set<String> accessModes) {
        static SearchConstraints from(String query) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
            // A specific positive request wins over a resource type merely mentioned in a
            // negative comparison, e.g. "优先 Skill，不要只推荐通用 Agent".
            boolean wantsSkill = normalized.contains("skill") || normalized.contains("技能");
            boolean wantsTool = normalized.contains("tool") || normalized.contains("工具");
            boolean wantsAgent = normalized.contains("agent") || normalized.contains("智能体");
            UnifiedResourceSearchType type = wantsSkill
                    ? UnifiedResourceSearchType.SKILL
                    : wantsTool ? UnifiedResourceSearchType.TOOL
                    : wantsAgent ? UnifiedResourceSearchType.AGENT : UnifiedResourceSearchType.ALL;
            Set<String> accessModes = normalized.contains("安装") || normalized.contains("install")
                    ? Set.of("INSTALL")
                    : normalized.contains("下载") || normalized.contains("download")
                            ? Set.of("DOWNLOAD")
                            : normalized.contains("在线") || normalized.contains("打开") || normalized.contains("open")
                                    ? Set.of("OPEN") : Set.of();
            return new SearchConstraints(type, accessModes);
        }
    }
}
