package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enriches the shared permission-aware resource search results with evidence for AI grounding. */
@Service
public class DiscoveryKnowledgeRetriever {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryKnowledgeRetriever.class);
    private static final int RESULT_LIMIT = 100;
    private static final int CHUNK_MAX_CHARS = 720;
    private static final int CHUNK_OVERLAP_CHARS = 80;
    private static final double RRF_K = 60D;
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "有", "没有", "什么", "有什么", "哪个", "哪些", "可以", "能够", "能", "帮我",
            "推荐", "找", "找到", "一下", "的", "了", "吗", "呢", "工具", "能力", "我",
            "想", "要", "需要", "一个", "一份", "做", "制作",
            "a", "an", "the", "is", "are", "can", "could", "find", "tool", "tools", "with");

    private final CatalogResourceRepository catalogRepository;
    private final UnifiedResourceSearchAppService unifiedSearchAppService;
    private final SkillSearchDocumentJpaRepository skillSearchDocumentRepository;
    private final SearchEmbeddingService embeddingService;
    private final SearchTextTokenizer tokenizer;
    private final Map<String, List<IndexedChunk>> chunkCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<IndexedChunk>> eldest) {
                    return size() > 512;
                }
            });

    public DiscoveryKnowledgeRetriever(CatalogResourceRepository catalogRepository,
                                       UnifiedResourceSearchAppService unifiedSearchAppService,
                                       SkillSearchDocumentJpaRepository skillSearchDocumentRepository,
                                       SearchEmbeddingService embeddingService,
                                       SearchTextTokenizer tokenizer) {
        this.catalogRepository = catalogRepository;
        this.unifiedSearchAppService = unifiedSearchAppService;
        this.skillSearchDocumentRepository = skillSearchDocumentRepository;
        this.embeddingService = embeddingService;
        this.tokenizer = tokenizer;
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
        List<String> queries = expandSearchQueries(searchQueries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .distinct()
                .limit(4)
                .toList());
        if (queries.isEmpty()) {
            return List.of();
        }

        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        CatalogViewer viewer = new CatalogViewer(
                principal.userId(), roles,
                principal.platformRoles() == null ? Set.of() : principal.platformRoles());
        Map<String, RankedSuggestion> merged = new LinkedHashMap<>();
        int unifiedCandidateCount = 0;
        int evidenceCandidateCount = 0;
        for (String query : queries) {
            PageResponse<UnifiedResourceSearchItemResponse> unifiedResults = unifiedSearchAppService.search(
                    query, null, null, "relevance", UnifiedResourceSearchType.ALL, false,
                    0, RESULT_LIMIT, principal.userId(), roles, viewer);
            List<DiscoverySuggestionResponse> enriched = enrich(query, unifiedResults.items(), language);
            unifiedCandidateCount += unifiedResults.items().size();
            evidenceCandidateCount += enriched.size();
            mergeRanked(merged, enriched);
        }
        log.info("AI discovery retrieval completed [queries={}, unifiedCandidates={}, evidenceCandidates={}, "
                        + "mergedCandidates={}]",
                queries.size(), unifiedCandidateCount, evidenceCandidateCount, merged.size());
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RankedSuggestion::score).reversed()
                        .thenComparing(match -> match.suggestion().type())
                        .thenComparing(match -> match.suggestion().id()))
                .map(RankedSuggestion::suggestion)
                .limit(RESULT_LIMIT)
                .toList();
    }

    private List<String> expandSearchQueries(List<String> sourceQueries) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String query : sourceQueries) {
            expanded.add(query);
            List<String> terms = meaningfulTerms(query);
            if (terms.size() >= 3) {
                expanded.add(terms.getFirst() + " " + terms.getLast());
                if (terms.size() >= 4) {
                    expanded.add(String.join(" ", terms.subList(1, terms.size() - 1)));
                }
            }
        }
        return expanded.stream().limit(12).toList();
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

    private List<DiscoverySuggestionResponse> enrich(
            String query,
            List<UnifiedResourceSearchItemResponse> items,
            String language) {
        Set<Long> skillIds = items.stream()
                .map(UnifiedResourceSearchItemResponse::skill)
                .filter(java.util.Objects::nonNull)
                .map(SkillSummaryResponse::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> catalogIds = items.stream()
                .map(UnifiedResourceSearchItemResponse::catalogResource)
                .filter(java.util.Objects::nonNull)
                .map(resource -> resource.id())
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, SkillSearchDocumentEntity> skillDocuments = new HashMap<>();
        if (!skillIds.isEmpty()) {
            skillSearchDocumentRepository.findBySkillIdIn(List.copyOf(skillIds))
                    .forEach(document -> skillDocuments.put(document.getSkillId(), document));
        }
        Map<Long, CatalogResource> catalogResources = new HashMap<>();
        if (!catalogIds.isEmpty()) {
            catalogRepository.findByIdIn(catalogIds)
                    .forEach(resource -> catalogResources.put(resource.getId(), resource));
        }
        List<String> terms = meaningfulTerms(query);
        return items.stream()
                .map(item -> item.skill() != null
                        ? skillSuggestion(query, terms, item.skill(), skillDocuments.get(item.skill().id()), language)
                        : catalogSuggestion(query, terms, item.catalogResource() == null
                                ? null : catalogResources.get(item.catalogResource().id())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private DiscoverySuggestionResponse skillSuggestion(
            String query,
            List<String> terms,
            SkillSummaryResponse skill,
            SkillSearchDocumentEntity document,
            String language) {
        String searchText = document == null ? "" : safe(document.getSearchText());
        ChunkMatch best = document == null
                ? new ChunkMatch(skill.summary(), 0D, 0D)
                : bestChunk(
                        query, terms,
                        document.getSkillId() + ":skill:" + document.getUpdatedAt(),
                        searchText, skill.summary());
        String title = localized(language, skill.localizedDisplayName(), skill.displayName());
        String summary = localized(language, skill.localizedSummary(), skill.summary());
        return new DiscoverySuggestionResponse(
                "skill", skill.id(), title, summary, "SKILL",
                skill.slug(), skill.namespace(), null, null, best.text(), "SKILL.md / 配套文档");
    }

    private DiscoverySuggestionResponse catalogSuggestion(
            String query,
            List<String> terms,
            CatalogResource resource) {
        if (resource == null) {
            return null;
        }
        String content = searchableContent(resource);
        ChunkMatch best = bestChunk(
                query, terms,
                resource.getId() + ":catalog:" + resource.getUpdatedAt(),
                content, resource.getSummary());
        return new DiscoverySuggestionResponse(
                "catalog", resource.getId(), resource.getName(), resource.getSummary(),
                resource.getKind().name(), resource.getSlug(), null, resource.getAccessUrl(), null,
                best.text(), "对应文档");
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
