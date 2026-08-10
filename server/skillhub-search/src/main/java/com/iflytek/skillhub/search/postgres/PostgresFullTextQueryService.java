package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchDocument;
import com.iflytek.skillhub.search.ResourceSearchQueryInterpreter;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL-backed implementation of {@link SearchQueryService}.
 *
 * <p>The query pipeline combines structured visibility filters, full-text
 * ranking, and an optional semantic re-ranking pass over a bounded candidate
 * set.
 *
 * <p>This class is a deliberate direct-persistence exception. Search ranking,
 * FTS predicates, and candidate-window tuning are storage-engine-specific
 * concerns, so keeping the native SQL close to the search adapter is clearer
 * than forcing that logic through domain repository ports or generic
 * controller-facing query repositories.
 */
@Service
public class PostgresFullTextQueryService implements SearchQueryService {
    private static final int MAX_QUERY_TERMS = 8;
    private static final int SHORT_PREFIX_LENGTH = 2;
    private static final String TITLE_VECTOR_SQL = "to_tsvector('simple', coalesce(title, ''))";
    private static final String TITLE_SQL = "LOWER(title)";
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "我", "想", "要", "做", "帮", "帮我", "请", "需要", "一份", "一下", "有没有", "什么", "哪个", "哪些",
            "i", "want", "need", "please", "help", "make", "find", "a", "an", "the", "for", "me");

    private final EntityManager entityManager;
    private final SkillSearchDocumentJpaRepository searchDocumentRepository;
    private final SearchEmbeddingService searchEmbeddingService;
    private final SearchTextTokenizer searchTextTokenizer;
    private final HybridResourceSearchRanker resourceSearchRanker;
    private final boolean semanticEnabled;
    private final int maxCandidates;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this(entityManager, null, null, new SearchTextTokenizer(), false, 500);
    }

    @Autowired
    public PostgresFullTextQueryService(EntityManager entityManager,
                                        SkillSearchDocumentJpaRepository searchDocumentRepository,
                                        SearchEmbeddingService searchEmbeddingService,
                                        SearchTextTokenizer searchTextTokenizer,
                                        @Value("${skillhub.search.semantic.enabled:true}") boolean semanticEnabled,
                                        @Value("${skillhub.search.semantic.max-candidates:500}") int maxCandidates) {
        this.entityManager = entityManager;
        this.searchDocumentRepository = searchDocumentRepository;
        this.searchEmbeddingService = searchEmbeddingService;
        this.searchTextTokenizer = searchTextTokenizer;
        this.resourceSearchRanker = searchEmbeddingService == null
                ? null
                : new HybridResourceSearchRanker(
                        searchEmbeddingService,
                        new ResourceSearchQueryInterpreter(searchTextTokenizer));
        this.semanticEnabled = semanticEnabled;
        this.maxCandidates = maxCandidates;
    }

    /**
     * Executes a search query against the denormalized search document table
     * and optionally re-ranks candidates using embeddings.
     */
    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        String tsQuery = buildPrefixTsQuery(normalizedKeyword);
        boolean hasKeyword = normalizedKeyword != null;
        boolean hasTsQuery = tsQuery != null;
        boolean useShortPrefixTitleSearch = hasTsQuery && isShortAsciiPrefixSearch(normalizedKeyword);
        boolean useSemanticRerank = semanticEnabled
                && hasKeyword
                && searchDocumentRepository != null
                && searchEmbeddingService != null;
        int requestedOffset = query.page() * query.size();
        if (useSemanticRerank && requestedOffset + query.size() > maxCandidates) {
            useSemanticRerank = false;
        }
        // Hybrid search must recall documents that have no lexical overlap with the request.
        // Visibility and lifecycle constraints bound the semantic candidate corpus first.
        boolean useLexicalFilter = hasKeyword && !useSemanticRerank;
        boolean useRelevanceOrdering = "relevance".equals(query.sortBy()) && hasKeyword;
        int sqlLimit = query.size();
        int sqlOffset = requestedOffset;
        if (useSemanticRerank) {
            sqlLimit = maxCandidates;
            sqlOffset = 0;
        }
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.skill_id ");
        sql.append("FROM skill_search_document d ");
        sql.append("JOIN skill s ON s.id = d.skill_id ");
        sql.append("JOIN namespace n ON n.id = d.namespace_id ");
        if (query.requireInstallableLatest()) {
            sql.append("JOIN skill_version latest ON latest.id = s.latest_version_id ");
        }
        sql.append("WHERE 1=1 ");

        // Visibility filtering
        sql.append("AND (d.visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespace_id IN :memberNamespaceIds) ");
        }
        sql.append(") ");

        // Status filtering
        sql.append("AND d.status = 'ACTIVE' ");
        sql.append("AND s.status = 'ACTIVE' ");
        sql.append("AND s.hidden = FALSE ");
        if (query.requireInstallableLatest()) {
            sql.append("AND latest.status = 'PUBLISHED' ");
            sql.append("AND latest.download_ready = TRUE ");
            sql.append("AND latest.yanked_at IS NULL ");
        }
        sql.append("AND (n.status <> 'ARCHIVED' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR d.namespace_id IN :memberNamespaceIds ");
        }
        sql.append(") ");

        // Namespace filtering
        if (query.namespaceId() != null) {
            sql.append("AND d.namespace_id = :namespaceId ");
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            sql.append("AND d.skill_id IN (");
            sql.append("SELECT sl.skill_id FROM skill_label sl ");
            sql.append("JOIN label_definition ld ON ld.id = sl.label_id ");
            sql.append("WHERE LOWER(ld.slug) IN :labelSlugs");
            sql.append(") ");
        }

        // Full-text search
        if (useLexicalFilter) {
            sql.append("AND (");
            if (hasTsQuery) {
                if (useShortPrefixTitleSearch) {
                    sql.append(TITLE_VECTOR_SQL).append(" @@ to_tsquery('simple', :tsQuery) ");
                } else {
                    sql.append("d.search_vector @@ to_tsquery('simple', :tsQuery) ");
                }
                sql.append(" OR ");
            }
            sql.append(TITLE_SQL).append(" LIKE :titleLike");
            sql.append(") ");
        }

        // Sorting
        if ("downloads".equals(query.sortBy())) {
            sql.append("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("rating".equals(query.sortBy())) {
            sql.append("ORDER BY s.rating_avg DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("newest".equals(query.sortBy())) {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        } else if (useRelevanceOrdering) {
            sql.append("ORDER BY CASE ");
            sql.append("WHEN ").append(TITLE_SQL).append(" = :titleExact THEN 4 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titlePrefix THEN 3 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titleLike THEN 2 ");
            sql.append("ELSE 1 END DESC, ");
            if (useShortPrefixTitleSearch) {
                sql.append("ts_rank_cd(").append(TITLE_VECTOR_SQL)
                        .append(", to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else if (hasTsQuery) {
                sql.append("ts_rank_cd(d.search_vector, to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else {
                sql.append("d.updated_at DESC, d.skill_id DESC ");
            }
        } else {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        }

        // Pagination
        sql.append("LIMIT :limit OFFSET :offset");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());

        if (query.visibilityScope().userId() != null) {
            nativeQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
        }

        if (query.namespaceId() != null) {
            nativeQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            nativeQuery.setParameter("labelSlugs", query.labelSlugs());
        }

        if (useLexicalFilter || useRelevanceOrdering) {
            if (hasTsQuery) {
                nativeQuery.setParameter("tsQuery", tsQuery);
            }
            if (useRelevanceOrdering) {
                nativeQuery.setParameter("titleExact", normalizedKeyword.toLowerCase(Locale.ROOT));
                nativeQuery.setParameter("titlePrefix", normalizedKeyword.toLowerCase(Locale.ROOT) + "%");
            }
            nativeQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%");
        }

        nativeQuery.setParameter("limit", sqlLimit);
        nativeQuery.setParameter("offset", sqlOffset);

        @SuppressWarnings("unchecked")
        List<Long> skillIds = (List<Long>) nativeQuery.getResultList().stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        // Count total
        String countSql = sql.toString().replaceFirst("SELECT d\\.skill_id", "SELECT COUNT(*)");
        int orderByIndex = countSql.indexOf("ORDER BY");
        if (orderByIndex >= 0) {
            countSql = countSql.substring(0, orderByIndex);
        }
        int limitIndex = countSql.indexOf("LIMIT");
        if (limitIndex >= 0) {
            countSql = countSql.substring(0, limitIndex);
        }

        Query countQuery = entityManager.createNativeQuery(countSql);

        if (query.visibilityScope().userId() != null) {
            countQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
        }

        if (query.namespaceId() != null) {
            countQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            countQuery.setParameter("labelSlugs", query.labelSlugs());
        }

        if (useLexicalFilter) {
            if (hasTsQuery) {
                countQuery.setParameter("tsQuery", tsQuery);
            }
            countQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%");
        }

        long total = ((Number) countQuery.getSingleResult()).longValue();

        if (useSemanticRerank && !skillIds.isEmpty()) {
            SemanticRecallResult semanticResult = recallByHybridSimilarity(
                    skillIds, normalizedKeyword, query.sortBy(), requestedOffset, query.size());
            return new SearchResult(
                    semanticResult.skillIds(), semanticResult.total(), query.page(), query.size());
        }

        return new SearchResult(skillIds, total, query.page(), query.size());
    }

    private SemanticRecallResult recallByHybridSimilarity(List<Long> candidateSkillIds,
                                                          String normalizedKeyword,
                                                          String sortBy,
                                                          int requestedOffset,
                                                          int pageSize) {
        Map<Long, SkillSearchDocumentEntity> documentsBySkillId = new HashMap<>();
        for (SkillSearchDocumentEntity entity : searchDocumentRepository.findBySkillIdIn(candidateSkillIds)) {
            documentsBySkillId.put(entity.getSkillId(), entity);
        }
        List<ResourceSearchDocument> documents = candidateSkillIds.stream()
                .map(documentsBySkillId::get)
                .filter(java.util.Objects::nonNull)
                .map(entity -> new ResourceSearchDocument(
                        entity.getSkillId().toString(),
                        "SKILL",
                        entity.getTitle(),
                        entity.getTitle(),
                        entity.getSummary(),
                        List.of(),
                        entity.getKeywords() == null ? List.of() : List.of(entity.getKeywords()),
                        entity.getSearchText(),
                        "INSTALL",
                        entity.getSemanticVector(),
                        0D))
                .toList();
        List<HybridResourceSearchRanker.RankedResource> ranked = resourceSearchRanker.rankWithinScope(
                normalizedKeyword, documents, maxCandidates, false);
        List<Long> relevantIds = ranked.stream()
                .map(match -> Long.parseLong(match.document().id()))
                .toList();
        List<Long> orderedIds;
        if ("relevance".equals(sortBy)) {
            orderedIds = relevantIds;
        } else {
            Set<Long> relevantSet = Set.copyOf(relevantIds);
            orderedIds = candidateSkillIds.stream().filter(relevantSet::contains).toList();
        }
        List<Long> pageIds = orderedIds.stream()
                .skip(requestedOffset)
                .limit(pageSize)
                .toList();
        return new SemanticRecallResult(pageIds, orderedIds.size());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String buildPrefixTsQuery(String keyword) {
        if (keyword == null) {
            return null;
        }

        List<String> terms = searchTextTokenizer.tokenizeForQuery(keyword).stream()
                .limit(MAX_QUERY_TERMS)
                .filter(term -> !QUERY_STOP_WORDS.contains(term.toLowerCase(Locale.ROOT)))
                .toList();

        if (terms.isEmpty()) {
            return null;
        }

        List<String> tsQueryTerms = terms.stream()
                .filter(this::isTsQueryCompatibleTerm)
                .toList();

        if (tsQueryTerms.isEmpty()) {
            return null;
        }

        // Natural-language queries often contain filler words or only one of
        // several useful concepts. OR recall followed by ranking is more
        // forgiving than requiring every Jieba token to be present.
        return tsQueryTerms.stream()
                .map(term -> usePrefixMatch(term) ? term + ":*" : term)
                .reduce((left, right) -> left + " | " + right)
                .orElse(null);
    }

    private boolean isTsQueryCompatibleTerm(String term) {
        return term.chars().anyMatch(ch -> Character.isLetter(ch) || Character.isIdeographic(ch) || ch == '_');
    }

    private boolean usePrefixMatch(String term) {
        return term.chars().allMatch(ch -> ch < 128) && term.chars().anyMatch(Character::isLetter);
    }

    private boolean isShortAsciiPrefixSearch(String keyword) {
        if (keyword == null || keyword.length() > SHORT_PREFIX_LENGTH) {
            return false;
        }
        List<String> terms = searchTextTokenizer.tokenizeForQuery(keyword);
        return !terms.isEmpty() && terms.stream().allMatch(this::usePrefixMatch);
    }

    private record SemanticRecallResult(List<Long> skillIds, long total) {
    }
}
