package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-aware hybrid retrieval across catalog documents and fully indexed skill packages. */
@Service
public class DiscoveryKnowledgeRetriever {
    private static final int RESULT_LIMIT = 6;
    private static final double MIN_SEMANTIC_SCORE = 0.60D;
    private static final double RRF_K = 60D;
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "有", "没有", "什么", "有什么", "哪个", "哪些", "可以", "能够", "能", "帮我",
            "推荐", "找", "找到", "一下", "的", "了", "吗", "呢", "工具", "能力",
            "a", "an", "the", "is", "are", "can", "could", "find", "tool", "tools");

    private final CatalogResourceRepository catalogRepository;
    private final CatalogResourcePolicy catalogPolicy;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillSearchDocumentJpaRepository skillSearchDocumentRepository;
    private final SearchEmbeddingService embeddingService;
    private final SearchTextTokenizer tokenizer;
    private final Map<String, String> catalogEmbeddingCache = new ConcurrentHashMap<>();

    public DiscoveryKnowledgeRetriever(CatalogResourceRepository catalogRepository,
                                       CatalogResourcePolicy catalogPolicy,
                                       SkillSearchAppService skillSearchAppService,
                                       SkillSearchDocumentJpaRepository skillSearchDocumentRepository,
                                       SearchEmbeddingService embeddingService,
                                       SearchTextTokenizer tokenizer) {
        this.catalogRepository = catalogRepository;
        this.catalogPolicy = catalogPolicy;
        this.skillSearchAppService = skillSearchAppService;
        this.skillSearchDocumentRepository = skillSearchDocumentRepository;
        this.embeddingService = embeddingService;
        this.tokenizer = tokenizer;
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(String question,
                                                      PlatformPrincipal principal,
                                                      Map<Long, NamespaceRole> namespaceRoles) {
        return retrieve(List.of(question), principal, namespaceRoles);
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(List<String> searchQueries,
                                                      PlatformPrincipal principal,
                                                      Map<Long, NamespaceRole> namespaceRoles) {
        String retrievalQuery = searchQueries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .distinct()
                .limit(4)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        Set<Long> namespaceIds = namespaceRoles == null ? Set.of() : namespaceRoles.keySet();
        List<DiscoverySuggestionResponse> catalog = retrieveCatalog(
                retrievalQuery, principal, namespaceIds).stream().limit(4).toList();
        List<DiscoverySuggestionResponse> skills = retrieveSkills(
                retrievalQuery, principal.userId(), namespaceRoles == null ? Map.of() : namespaceRoles);

        List<DiscoverySuggestionResponse> merged = new ArrayList<>(RESULT_LIMIT);
        merged.addAll(catalog);
        skills.stream().limit(RESULT_LIMIT - merged.size()).forEach(merged::add);
        if (merged.size() < RESULT_LIMIT) {
            catalog.stream().skip(merged.size()).limit(RESULT_LIMIT - merged.size()).forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private List<DiscoverySuggestionResponse> retrieveCatalog(String question,
                                                               PlatformPrincipal principal,
                                                               Set<Long> namespaceIds) {
        List<CatalogResource> visible = catalogRepository.findAll().stream()
                .filter(resource -> resource.getStatus() == CatalogResourceStatus.PUBLISHED)
                .filter(resource -> catalogPolicy.canView(
                        resource, principal.userId(), namespaceIds,
                        principal.platformRoles().contains("SUPER_ADMIN")))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }

        List<String> terms = meaningfulTerms(question);
        Map<Long, Double> semanticScores = new HashMap<>();
        Map<Long, Double> lexicalScores = new HashMap<>();
        for (CatalogResource resource : visible) {
            String content = searchableContent(resource);
            String cacheKey = resource.getId() + ":" + resource.getUpdatedAt();
            String vector = catalogEmbeddingCache.computeIfAbsent(cacheKey, ignored -> embeddingService.embed(content));
            semanticScores.put(resource.getId(), embeddingService.similarity(question, vector));
            lexicalScores.put(resource.getId(), lexicalScore(terms, content));
        }

        Map<Long, Integer> semanticRanks = ranks(visible, semanticScores);
        Map<Long, Integer> lexicalRanks = ranks(visible.stream()
                .filter(resource -> lexicalScores.getOrDefault(resource.getId(), 0D) > 0D).toList(), lexicalScores);

        return visible.stream()
                .map(resource -> new RankedCatalog(
                        resource,
                        rrfScore(resource.getId(), semanticRanks, lexicalRanks),
                        semanticScores.getOrDefault(resource.getId(), 0D),
                        lexicalScores.getOrDefault(resource.getId(), 0D)))
                .filter(match -> match.semanticScore() >= MIN_SEMANTIC_SCORE || match.lexicalScore() > 0D)
                .sorted(Comparator.comparingDouble(RankedCatalog::score).reversed()
                        .thenComparing(match -> match.resource().getUpdatedAt(), Comparator.reverseOrder()))
                .map(match -> catalogSuggestion(match.resource(), bestExcerpt(
                        question, match.resource().getDocumentation(), match.resource().getSummary())))
                .toList();
    }

    private List<DiscoverySuggestionResponse> retrieveSkills(String question,
                                                              String userId,
                                                              Map<Long, NamespaceRole> namespaceRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                question, null, "relevance", 0, RESULT_LIMIT, userId, namespaceRoles);
        List<Long> ids = response.items().stream().map(SkillSummaryResponse::id).toList();
        Map<Long, SkillSearchDocumentEntity> documents = new HashMap<>();
        skillSearchDocumentRepository.findBySkillIdIn(ids)
                .forEach(document -> documents.put(document.getSkillId(), document));
        List<String> terms = meaningfulTerms(question);
        return response.items().stream().map(skill -> {
            SkillSearchDocumentEntity document = documents.get(skill.id());
            String searchText = document == null ? "" : safe(document.getSearchText());
            double lexicalScore = lexicalScore(terms, searchText);
            double semanticScore = document == null || document.getSemanticVector() == null
                    ? 0D : embeddingService.similarity(question, document.getSemanticVector());
            String evidence = document == null ? null : bestExcerpt(
                    question, searchText, skill.summary());
            return new RankedSkill(new DiscoverySuggestionResponse(
                    "skill", skill.id(), skill.displayName(), skill.summary(), "SKILL",
                    skill.slug(), skill.namespace(), null, null, evidence, "SKILL.md / 配套文档"),
                    semanticScore, lexicalScore);
        }).filter(match -> match.semanticScore() >= MIN_SEMANTIC_SCORE || match.lexicalScore() > 0D)
                .sorted(Comparator.comparingDouble(DiscoveryKnowledgeRetriever::skillScore).reversed())
                .map(RankedSkill::suggestion)
                .limit(RESULT_LIMIT)
                .toList();
    }

    private DiscoverySuggestionResponse catalogSuggestion(CatalogResource resource, String evidence) {
        return new DiscoverySuggestionResponse(
                "catalog", resource.getId(), resource.getName(), resource.getSummary(),
                resource.getKind().name(), resource.getSlug(), null, resource.getAccessUrl(), null,
                evidence, "对应文档");
    }

    private Map<Long, Integer> ranks(List<CatalogResource> resources, Map<Long, Double> scores) {
        Map<Long, Integer> ranks = new HashMap<>();
        List<CatalogResource> sorted = resources.stream()
                .sorted(Comparator.comparingDouble(
                        (CatalogResource resource) -> scores.getOrDefault(resource.getId(), 0D)).reversed())
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            ranks.put(sorted.get(index).getId(), index + 1);
        }
        return ranks;
    }

    private double rrfScore(Long id, Map<Long, Integer> semanticRanks, Map<Long, Integer> lexicalRanks) {
        double score = semanticRanks.containsKey(id) ? 1D / (RRF_K + semanticRanks.get(id)) : 0D;
        score += lexicalRanks.containsKey(id) ? 1D / (RRF_K + lexicalRanks.get(id)) : 0D;
        return score;
    }

    private double lexicalScore(List<String> terms, String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return terms.stream().filter(term -> normalized.contains(term.toLowerCase(Locale.ROOT))).count();
    }

    private List<String> meaningfulTerms(String question) {
        return tokenizer.tokenizeForQuery(question).stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .filter(term -> term.length() > 1)
                .filter(term -> !QUERY_STOP_WORDS.contains(term))
                .toList();
    }

    private static double skillScore(RankedSkill match) {
        return match.semanticScore() + Math.min(match.lexicalScore(), 4D) * 0.2D;
    }

    private String searchableContent(CatalogResource resource) {
        return String.join("\n",
                safe(resource.getName()), safe(resource.getSummary()),
                String.join(" ", resource.getScenarios()), String.join(" ", resource.getTags()),
                safe(resource.getDocumentation()));
    }

    static String bestExcerpt(String question, String document, String fallback) {
        if (document == null || document.isBlank()) {
            return fallback;
        }
        List<String> terms = new SearchTextTokenizer().tokenizeForQuery(question);
        String best = null;
        long bestScore = -1;
        for (String paragraph : document.split("(?:\\R\\s*){2,}|(?=#+\\s)")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (normalized.length() < 12) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            long score = terms.stream().filter(term -> lower.contains(term.toLowerCase(Locale.ROOT))).count();
            if (score > bestScore) {
                best = normalized;
                bestScore = score;
            }
        }
        if (best == null) {
            best = document.replaceAll("\\s+", " ").trim();
        }
        return best.length() <= 220 ? best : best.substring(0, 217) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record RankedCatalog(CatalogResource resource, double score,
                                 double semanticScore, double lexicalScore) {
    }

    private record RankedSkill(DiscoverySuggestionResponse suggestion,
                               double semanticScore, double lexicalScore) {
    }
}
