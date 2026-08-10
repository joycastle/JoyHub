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
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchDocument;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-aware hybrid retrieval across catalog documents and fully indexed skill packages. */
@Service
public class DiscoveryKnowledgeRetriever {
    private static final int RESULT_LIMIT = 100;
    private static final int FULL_CATALOG_CONTEXT_LIMIT = 40;
    private static final int CHUNK_MAX_CHARS = 720;
    private static final int CHUNK_OVERLAP_CHARS = 80;
    /**
     * Search must return a wider candidate window than the number shown in the
     * answer. The portal search intentionally applies recency as a tie-breaker;
     * AI discovery performs its own semantic/lexical rerank after retrieval.
     */
    private static final int SKILL_CANDIDATE_LIMIT = 40;
    private static final int SKILL_SEMANTIC_POOL_LIMIT = 500;
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
    private final HybridResourceSearchRanker resourceSearchRanker;
    private final Map<String, List<IndexedChunk>> chunkCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<IndexedChunk>> eldest) {
                    return size() > 512;
                }
            });

    public DiscoveryKnowledgeRetriever(CatalogResourceRepository catalogRepository,
                                       CatalogResourcePolicy catalogPolicy,
                                       SkillSearchAppService skillSearchAppService,
                                       SkillSearchDocumentJpaRepository skillSearchDocumentRepository,
                                       SearchEmbeddingService embeddingService,
                                       SearchTextTokenizer tokenizer,
                                       HybridResourceSearchRanker resourceSearchRanker) {
        this.catalogRepository = catalogRepository;
        this.catalogPolicy = catalogPolicy;
        this.skillSearchAppService = skillSearchAppService;
        this.skillSearchDocumentRepository = skillSearchDocumentRepository;
        this.embeddingService = embeddingService;
        this.tokenizer = tokenizer;
        this.resourceSearchRanker = resourceSearchRanker;
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(String question,
                                                      PlatformPrincipal principal,
                                                      Map<Long, NamespaceRole> namespaceRoles) {
        return retrieve(List.of(question), principal, namespaceRoles, "zh-CN");
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(List<String> searchQueries,
                                                      PlatformPrincipal principal,
                                                      Map<Long, NamespaceRole> namespaceRoles) {
        return retrieve(searchQueries, principal, namespaceRoles, "zh-CN");
    }

    @Transactional(readOnly = true)
    public List<DiscoverySuggestionResponse> retrieve(List<String> searchQueries,
                                                      PlatformPrincipal principal,
                                                      Map<Long, NamespaceRole> namespaceRoles,
                                                      String language) {
        List<String> queries = searchQueries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .distinct()
                .limit(4)
                .toList();
        if (queries.isEmpty()) {
            return List.of();
        }

        Set<Long> namespaceIds = namespaceRoles == null ? Set.of() : namespaceRoles.keySet();
        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        SkillSearchAppService.SearchResponse semanticPool = skillSearchAppService.search(
                null, null, "newest", 0, SKILL_SEMANTIC_POOL_LIMIT, principal.userId(), roles);
        Map<String, RankedSuggestion> merged = new LinkedHashMap<>();
        for (String query : queries) {
            var intent = resourceSearchRanker.interpret(query);
            boolean catalogRequested = intent.resourceTypes().isEmpty()
                    || intent.resourceTypes().contains("AGENT")
                    || intent.resourceTypes().contains("TOOL");
            boolean skillRequested = intent.resourceTypes().isEmpty()
                    || intent.resourceTypes().contains("SKILL");
            if (catalogRequested) {
                mergeRanked(merged, retrieveCatalog(query, principal, namespaceIds));
            }
            if (skillRequested) {
                mergeRanked(merged, retrieveSkills(
                        query, principal.userId(), roles, language, semanticPool.items()));
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RankedSuggestion::score).reversed()
                        .thenComparing(match -> match.suggestion().type())
                        .thenComparing(match -> match.suggestion().id()))
                .map(RankedSuggestion::suggestion)
                .limit(RESULT_LIMIT)
                .toList();
    }

    private void mergeRanked(Map<String, RankedSuggestion> merged,
                             List<DiscoverySuggestionResponse> suggestions) {
        for (int index = 0; index < suggestions.size(); index++) {
            DiscoverySuggestionResponse suggestion = suggestions.get(index);
            String key = suggestion.type() + ":" + suggestion.id();
            double contribution = 1D / (RRF_K + index + 1D);
            RankedSuggestion current = merged.get(key);
            if (current == null) {
                merged.put(key, new RankedSuggestion(suggestion, contribution));
            } else {
                merged.put(key, new RankedSuggestion(current.suggestion(), current.score() + contribution));
            }
        }
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

        Map<Long, String> evidenceByResource = new HashMap<>();
        for (CatalogResource resource : visible) {
            String content = searchableContent(resource);
            ChunkMatch best = bestChunk(
                    question,
                    meaningfulTerms(question),
                    resource.getId() + ":catalog:" + resource.getUpdatedAt(),
                    content,
                    resource.getSummary());
            evidenceByResource.put(resource.getId(), best.text());
        }
        Map<String, CatalogResource> resourcesById = visible.stream()
                .collect(java.util.stream.Collectors.toMap(resource -> resource.getId().toString(), resource -> resource));
        return resourceSearchRanker.rank(
                        question,
                        visible.stream().map(this::catalogSearchDocument).toList(),
                        RESULT_LIMIT,
                        visible.size() <= FULL_CATALOG_CONTEXT_LIMIT).stream()
                .map(match -> resourcesById.get(match.document().id()))
                .filter(java.util.Objects::nonNull)
                .map(resource -> catalogSuggestion(resource, evidenceByResource.get(resource.getId())))
                .toList();
    }

    private ResourceSearchDocument catalogSearchDocument(CatalogResource resource) {
        return new ResourceSearchDocument(
                resource.getId().toString(),
                resource.getKind().name().equals("AGENT") ? "AGENT" : "TOOL",
                resource.getName(), resource.getSlug(), resource.getSummary(),
                List.copyOf(resource.getScenarios()), List.copyOf(resource.getTags()),
                resource.getDocumentation(),
                resource.getAccessUrl() != null && !resource.getAccessUrl().isBlank()
                        ? "OPEN" : resource.getArtifactStorageKey() != null ? "DOWNLOAD" : "OPEN",
                null, 0D);
    }

    private List<DiscoverySuggestionResponse> retrieveSkills(String question,
                                                              String userId,
                                                              Map<Long, NamespaceRole> namespaceRoles,
                                                              String language,
                                                              List<SkillSummaryResponse> semanticPool) {
        SkillSearchAppService.SearchResponse lexicalResponse = skillSearchAppService.search(
                question, null, "relevance", 0, SKILL_CANDIDATE_LIMIT, userId, namespaceRoles);
        Map<Long, SkillSummaryResponse> skillsById = new HashMap<>();
        lexicalResponse.items().forEach(skill -> skillsById.put(skill.id(), skill));
        semanticPool.forEach(skill -> skillsById.putIfAbsent(skill.id(), skill));
        List<Long> ids = List.copyOf(skillsById.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillSearchDocumentEntity> documents = new HashMap<>();
        skillSearchDocumentRepository.findBySkillIdIn(ids)
                .forEach(document -> documents.put(document.getSkillId(), document));
        List<String> terms = meaningfulTerms(question);
        Map<String, DiscoverySuggestionResponse> suggestionsById = new HashMap<>();
        List<ResourceSearchDocument> searchDocuments = skillsById.values().stream().map(skill -> {
            SkillSearchDocumentEntity document = documents.get(skill.id());
            String searchText = document == null ? "" : safe(document.getSearchText());
            ChunkMatch best = document == null
                    ? new ChunkMatch(skill.summary(), 0D, 0D)
                    : bestChunk(
                            question,
                            terms,
                            document.getSkillId() + ":skill:" + document.getUpdatedAt(),
                            searchText,
                            skill.summary());
            String title = localized(language, skill.localizedDisplayName(), skill.displayName());
            String summary = localized(language, skill.localizedSummary(), skill.summary());
            suggestionsById.put(skill.id().toString(), new DiscoverySuggestionResponse(
                    "skill", skill.id(), title, summary, "SKILL",
                    skill.slug(), skill.namespace(), null, null, best.text(), "SKILL.md / 配套文档"));
            return new ResourceSearchDocument(
                    skill.id().toString(), "SKILL", title, skill.slug(), summary,
                    List.of(),
                    document == null || document.getKeywords() == null
                            ? List.of() : List.of(document.getKeywords()),
                    searchText, "INSTALL",
                    document == null ? null : document.getSemanticVector(),
                    0D);
        }).toList();
        return resourceSearchRanker.rankWithinScope(
                        question, searchDocuments, RESULT_LIMIT, searchDocuments.size() <= RESULT_LIMIT).stream()
                .map(match -> suggestionsById.get(match.document().id()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private DiscoverySuggestionResponse catalogSuggestion(CatalogResource resource, String evidence) {
        return new DiscoverySuggestionResponse(
                "catalog", resource.getId(), resource.getName(), resource.getSummary(),
                resource.getKind().name(), resource.getSlug(), null, resource.getAccessUrl(), null,
                evidence, "对应文档");
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

    /**
     * Splits a resource document into small evidence units and scores the best unit for the query.
     * The chunks are cached in-process so the lightweight implementation does not require a new
     * vector database or a separate ingestion worker.
     */
    private ChunkMatch bestChunk(String question,
                                 List<String> terms,
                                 String cacheKey,
                                 String content,
                                 String fallback) {
        List<IndexedChunk> chunks;
        synchronized (chunkCache) {
            chunks = chunkCache.computeIfAbsent(cacheKey,
                    ignored -> chunk(content).stream()
                            .map(text -> new IndexedChunk(text, embeddingService.embed(text)))
                            .toList());
        }
        if (chunks.isEmpty()) {
            return new ChunkMatch(fallback, 0D, 0D);
        }
        return chunks.stream()
                .map(chunk -> new ChunkMatch(
                        chunk.text(),
                        embeddingService.similarity(question, chunk.vector()),
                        lexicalScore(terms, chunk.text())))
                .max(Comparator.comparingDouble(this::chunkScore))
                .orElse(new ChunkMatch(fallback, 0D, 0D));
    }

    private double chunkScore(ChunkMatch match) {
        return match.semanticScore() + Math.min(match.lexicalScore(), 4D) * 0.2D;
    }

    private String localized(String language, String localizedValue, String fallback) {
        if (language != null && language.toLowerCase(Locale.ROOT).startsWith("en")) {
            return safe(fallback).isBlank() ? safe(localizedValue) : fallback;
        }
        return safe(localizedValue).isBlank() ? safe(fallback) : localizedValue;
    }

    static List<String> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("(?m)(?=^#{1,6}\\s)|(?:\\R\\s*){2,}")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank()) {
                paragraphs.add(normalized);
            }
        }
        if (paragraphs.isEmpty()) {
            paragraphs.add(content.replaceAll("\\s+", " ").trim());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= CHUNK_MAX_CHARS) {
                if (current.length() > 0 && current.length() + paragraph.length() + 1 > CHUNK_MAX_CHARS) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(paragraph);
                continue;
            }
            if (current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            for (int start = 0; start < paragraph.length(); start += CHUNK_MAX_CHARS - CHUNK_OVERLAP_CHARS) {
                int end = Math.min(paragraph.length(), start + CHUNK_MAX_CHARS);
                chunks.add(paragraph.substring(start, end).trim());
                if (end == paragraph.length()) {
                    break;
                }
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks.stream().filter(chunk -> chunk.length() >= 12).toList();
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

    private record RankedSuggestion(DiscoverySuggestionResponse suggestion, double score) {
    }

    private record IndexedChunk(String text, String vector) {
    }

    private record ChunkMatch(String text, double semanticScore, double lexicalScore) {
    }
}
