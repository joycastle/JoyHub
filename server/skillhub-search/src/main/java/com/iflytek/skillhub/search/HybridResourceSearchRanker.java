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
        return rank(query, documents, limit, includeAllEligible);
    }

    private List<RankedResource> rank(ResourceSearchIntent intent,
                                      List<ResourceSearchDocument> documents,
                                      int limit,
                                      boolean includeAllEligible) {
        if (intent.normalizedQuery().isBlank()) {
            return documents.stream()
                    .map(document -> new RankedResource(document, document.qualityScore(), 0D, 0D, false))
                    .sorted(ordering())
                    .limit(limit)
                    .toList();
        }

        // Qualification is per document: another hit or a type filter cannot change its eligibility.
        return documents.stream()
                .map(document -> score(intent, document))
                .filter(result -> includeAllEligible || result.strongLexicalMatch()
                        || result.semanticScore() >= semanticMinimum(intent)
                        || intent.terms().isEmpty() && intent.hasStructuredConstraint())
                .sorted(ordering())
                .limit(limit)
                .toList();
    }

    private double semanticMinimum(ResourceSearchIntent intent) {
        // The deployed embedding model is Chinese; English-only semantic noise needs a stricter floor.
        // This floor depends on the query, never on which other resources or types are present.
        return intent.terms().stream().anyMatch(term -> term.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN))
                ? MIN_SEMANTIC_RECALL : 0.62D;
    }

    private RankedResource score(ResourceSearchIntent intent, ResourceSearchDocument document) {
        String title = normalize(document.title());
        String slug = normalize(document.slug());
        String summary = normalize(document.summary());
        String scenarios = normalize(String.join(" ", document.scenarios()));
        String tags = normalize(String.join(" ", document.tags()));
        String documentation = normalize(document.documentation());
        String fullText = searchableText(document);
        String highSignalText = String.join("\n", title, slug, summary, scenarios, tags);
        String identity = title + " " + slug;
        long identityMatches = intent.terms().stream().filter(term -> SearchTermMatcher.contains(identity, term)).count();
        long approximateIdentityMatches = intent.terms().stream()
                .filter(term -> SearchTermMatcher.contains(identity, term) || SearchTermMatcher.similarIdentityWord(identity, term)).count();
        double identityCoverage = intent.terms().isEmpty() ? 0D : (double) identityMatches / intent.terms().size();
        boolean approximateIdentity = !intent.terms().isEmpty() && identityMatches > 0
                && approximateIdentityMatches == intent.terms().size();

        double lexical = 0D;
        if (!intent.normalizedQuery().isBlank()) {
            if (title.equals(intent.normalizedQuery()) || slug.equals(intent.normalizedQuery())) {
                lexical += 4D;
            } else if (SearchTermMatcher.contains(title, intent.normalizedQuery()) || SearchTermMatcher.contains(slug, intent.normalizedQuery())) {
                lexical += 2.5D;
            }
        }
        for (String term : intent.terms()) {
            if (SearchTermMatcher.contains(title, term)) {
                lexical += 1.8D;
            } else if (approximateIdentity && SearchTermMatcher.similarIdentityWord(title, term)) {
                lexical += 0.9D;
            }
            if (SearchTermMatcher.contains(slug, term)) {
                lexical += 1.4D;
            } else if (approximateIdentity && SearchTermMatcher.similarIdentityWord(slug, term)) {
                lexical += 0.7D;
            }
            if (SearchTermMatcher.contains(tags, term)) {
                lexical += 1.2D;
            }
            if (SearchTermMatcher.contains(scenarios, term)) {
                lexical += 1.1D;
            }
            if (SearchTermMatcher.contains(summary, term)) {
                lexical += 0.8D;
            }
            if (SearchTermMatcher.contains(documentation, term)) {
                lexical += 0.25D;
            }
        }
        double semantic = similarity(intent.normalizedQuery(), document, fullText);
        long matchedHighSignalTerms = intent.terms().stream()
                .filter(term -> SearchTermMatcher.contains(highSignalText, term))
                .count();
        double termCoverage = intent.terms().isEmpty()
                ? 0D
                : (double) matchedHighSignalTerms / intent.terms().size();
        boolean phraseMatch = !intent.normalizedQuery().isBlank()
                && SearchTermMatcher.contains(highSignalText, intent.normalizedQuery());
        boolean strongLexicalMatch = phraseMatch || approximateIdentity
                || (intent.terms().size() == 1 && termCoverage == 1D)
                || (intent.terms().size() > 1 && termCoverage >= 0.66D);
        // Saturating normalization keeps title evidence distinct from repeated low-signal mentions.
        double normalizedLexical = lexical / (lexical + 6D);
        // A near word contributes half an exact word, and only for a fully matched product identity.
        double identityScore = approximateIdentity
                ? (identityMatches + (approximateIdentityMatches - identityMatches) * 0.5D) / intent.terms().size()
                : identityCoverage;
        double preference = (intent.resourceTypes().contains(document.resourceType()) ? 0.15D : 0D)
                + (intent.accessModes().stream().anyMatch(document.accessModes()::contains) ? 0.05D : 0D);
        double directPhrase = !intent.normalizedQuery().isBlank()
                && SearchTermMatcher.contains(summary, intent.normalizedQuery()) ? 0.16D : 0D;
        double score = normalizedLexical * 0.40D + semantic * 0.25D + termCoverage * 0.10D
                + identityScore * 0.20D + Math.min(Math.max(document.qualityScore(), 0D), 1D) * 0.05D
                + preference + directPhrase;
        return new RankedResource(document, score, semantic, lexical, strongLexicalMatch);
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
            double lexicalScore,
            boolean strongLexicalMatch
    ) {}
}
