package com.iflytek.skillhub.search;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Shared exact, full-text-like, and semantic ranker for every JoyHub resource shape. */
@Service
public class HybridResourceSearchRanker {
    private static final double MIN_SEMANTIC_RECALL = 0.50D;
    private static final int VECTOR_CACHE_SIZE = 1024;

    private final SearchEmbeddingService embeddingService;
    private final ResourceSearchQueryInterpreter interpreter;
    private final Map<String, String> vectorCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > VECTOR_CACHE_SIZE;
                }
            });

    public HybridResourceSearchRanker(SearchEmbeddingService embeddingService,
                                      ResourceSearchQueryInterpreter interpreter) {
        this.embeddingService = embeddingService;
        this.interpreter = interpreter;
    }

    public ResourceSearchIntent interpret(String query) {
        return interpreter.interpret(query);
    }

    public List<RankedResource> rank(String query,
                                     List<ResourceSearchDocument> documents,
                                     int limit,
                                     boolean includeAllEligible) {
        ResourceSearchIntent intent = interpreter.interpret(query);
        return rank(intent, documents, limit, includeAllEligible);
    }

    /** Uses the same ranking inside an endpoint that has already fixed resource and access scope. */
    public List<RankedResource> rankWithinScope(String query,
                                                List<ResourceSearchDocument> documents,
                                                int limit,
                                                boolean includeAllEligible) {
        ResourceSearchIntent parsed = interpreter.interpret(query);
        return rank(new ResourceSearchIntent(
                parsed.normalizedQuery(), parsed.terms(), java.util.Set.of(), java.util.Set.of()),
                documents, limit, includeAllEligible);
    }

    private List<RankedResource> rank(ResourceSearchIntent intent,
                                      List<ResourceSearchDocument> documents,
                                      int limit,
                                      boolean includeAllEligible) {
        if (intent.normalizedQuery().isBlank()) {
            return documents.stream()
                    .map(document -> new RankedResource(document, document.qualityScore(), 0D, 0D))
                    .sorted(ordering())
                    .limit(limit)
                    .toList();
        }

        return documents.stream()
                .filter(document -> matchesStructuredConstraints(intent, document))
                .map(document -> score(intent, document))
                .filter(result -> includeAllEligible
                        || result.lexicalScore() > 0D
                        || result.semanticScore() >= MIN_SEMANTIC_RECALL
                        || (intent.terms().isEmpty() && intent.hasStructuredConstraint()))
                .sorted(ordering())
                .limit(limit)
                .toList();
    }

    private RankedResource score(ResourceSearchIntent intent, ResourceSearchDocument document) {
        String title = normalize(document.title());
        String slug = normalize(document.slug());
        String summary = normalize(document.summary());
        String scenarios = normalize(String.join(" ", document.scenarios()));
        String tags = normalize(String.join(" ", document.tags()));
        String documentation = normalize(document.documentation());
        String fullText = searchableText(document);

        double lexical = 0D;
        if (!intent.normalizedQuery().isBlank()) {
            if (title.equals(intent.normalizedQuery()) || slug.equals(intent.normalizedQuery())) {
                lexical += 4D;
            } else if (title.contains(intent.normalizedQuery()) || slug.contains(intent.normalizedQuery())) {
                lexical += 2.5D;
            }
        }
        for (String term : intent.terms()) {
            if (title.contains(term)) {
                lexical += 1.8D;
            }
            if (slug.contains(term)) {
                lexical += 1.4D;
            }
            if (tags.contains(term)) {
                lexical += 1.2D;
            }
            if (scenarios.contains(term)) {
                lexical += 1.1D;
            }
            if (summary.contains(term)) {
                lexical += 0.8D;
            }
            if (documentation.contains(term)) {
                lexical += 0.25D;
            }
        }
        double semantic = similarity(intent.normalizedQuery(), document, fullText);
        double typeBoost = intent.resourceTypes().isEmpty() ? 0D : 1.4D;
        double accessBoost = intent.accessModes().isEmpty() ? 0D : 0.8D;
        double normalizedLexical = Math.min(lexical / 6D, 1D);
        double score = normalizedLexical * 0.35D
                + semantic * 0.30D
                + typeBoost * 0.20D
                + accessBoost * 0.10D
                + Math.min(Math.max(document.qualityScore(), 0D), 1D) * 0.05D;
        return new RankedResource(document, score, semantic, lexical);
    }

    private boolean matchesStructuredConstraints(ResourceSearchIntent intent, ResourceSearchDocument document) {
        if (!intent.resourceTypes().isEmpty()
                && !intent.resourceTypes().contains(normalizeType(document.resourceType()))) {
            return false;
        }
        return intent.accessModes().isEmpty()
                || intent.accessModes().contains(normalizeType(document.accessMode()));
    }

    private double similarity(String query, ResourceSearchDocument document, String fullText) {
        String vector = document.semanticVector();
        if (vector == null || vector.isBlank()) {
            String cacheKey = document.resourceType() + ":" + document.id() + ":" + Integer.toHexString(fullText.hashCode());
            synchronized (vectorCache) {
                vector = vectorCache.computeIfAbsent(cacheKey, ignored -> embeddingService.embed(fullText));
            }
        }
        return embeddingService.similarity(query, vector);
    }

    private String searchableText(ResourceSearchDocument document) {
        return String.join("\n",
                safe(document.title()), safe(document.slug()), safe(document.summary()),
                String.join(" ", document.scenarios()), String.join(" ", document.tags()),
                typeTerms(document.resourceType()), accessTerms(document.accessMode()),
                safe(document.documentation()));
    }

    private String typeTerms(String resourceType) {
        return switch (normalizeType(resourceType)) {
            case "AGENT" -> "Agent 智能体 机器人 AI 助手";
            case "TOOL" -> "Tool 工具 在线工具 内部工具";
            case "SKILL" -> "Skill 技能 可安装能力";
            default -> safe(resourceType);
        };
    }

    private String accessTerms(String accessMode) {
        return switch (normalizeType(accessMode)) {
            case "OPEN" -> "直接使用 在线打开 访问入口";
            case "INSTALL" -> "安装 使用 Skill";
            case "DOWNLOAD" -> "下载 获取资源包";
            default -> safe(accessMode);
        };
    }

    private Comparator<RankedResource> ordering() {
        return Comparator.comparingDouble(RankedResource::score).reversed()
                .thenComparing(result -> result.document().resourceType())
                .thenComparing(result -> result.document().id());
    }

    private String normalizeType(String value) {
        return safe(value).trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record RankedResource(
            ResourceSearchDocument document,
            double score,
            double semanticScore,
            double lexicalScore
    ) {}
}
