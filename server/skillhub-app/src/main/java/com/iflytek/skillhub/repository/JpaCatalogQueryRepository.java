package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog list projection uses JPQL because discovery combines element collections and
 * viewer-department visibility in one paginated query.
 */
@Repository
public class JpaCatalogQueryRepository implements CatalogQueryRepository {
    private final EntityManager entityManager;

    public JpaCatalogQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CatalogResource> searchPublished(
            String query,
            String center,
            CatalogResourceKind kind,
            String scenario,
            Long departmentId,
            Set<Long> viewerNamespaceIds,
            boolean superAdmin,
            Pageable pageable) {
        StringBuilder from = new StringBuilder(" FROM CatalogResource resource")
                .append(" LEFT JOIN resource.tags tag")
                .append(" LEFT JOIN resource.scenarios scenarioValue")
                .append(" LEFT JOIN resource.visibleNamespaceIds visibleNamespaceId")
                .append(" WHERE resource.status = :publishedStatus");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("publishedStatus", CatalogResourceStatus.PUBLISHED);

        if (!superAdmin) {
            from.append(" AND (resource.visibilityScope = :companyVisibility");
            parameters.put("companyVisibility", CatalogVisibilityScope.COMPANY);
            if (viewerNamespaceIds != null && !viewerNamespaceIds.isEmpty()) {
                from.append(" OR visibleNamespaceId IN :viewerNamespaceIds");
                parameters.put("viewerNamespaceIds", viewerNamespaceIds);
            }
            from.append(")");
        }

        if (kind != null) {
            from.append(" AND resource.kind = :kind");
            parameters.put("kind", kind);
        } else if (center != null && center.equalsIgnoreCase("AGENT")) {
            from.append(" AND resource.kind = :agentKind");
            parameters.put("agentKind", CatalogResourceKind.AGENT);
        } else if (center != null && center.equalsIgnoreCase("TOOL")) {
            from.append(" AND resource.kind <> :agentKind");
            parameters.put("agentKind", CatalogResourceKind.AGENT);
        }

        String normalizedQuery = normalize(query);
        if (normalizedQuery != null) {
            from.append(" AND (")
                    .append("LOWER(resource.name) LIKE :query")
                    .append(" OR LOWER(resource.slug) LIKE :query")
                    .append(" OR LOWER(resource.summary) LIKE :query")
                    .append(" OR LOWER(COALESCE(resource.documentation, '')) LIKE :query")
                    .append(" OR LOWER(tag) LIKE :query")
                    .append(" OR LOWER(scenarioValue) LIKE :query)");
            parameters.put("query", "%" + normalizedQuery + "%");
        }

        String normalizedScenario = normalize(scenario);
        if (normalizedScenario != null) {
            from.append(" AND LOWER(scenarioValue) = :scenario");
            parameters.put("scenario", normalizedScenario);
        }

        if (departmentId != null) {
            from.append(" AND resource.primaryNamespaceId = :departmentId");
            parameters.put("departmentId", departmentId);
        }

        TypedQuery<CatalogResource> contentQuery = entityManager.createQuery(
                "SELECT DISTINCT resource" + from + " ORDER BY resource.updatedAt DESC",
                CatalogResource.class
        );
        applyParameters(contentQuery, parameters);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(DISTINCT resource.id)" + from,
                Long.class
        );
        applyParameters(countQuery, parameters);

        return new PageImpl<>(contentQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    private static <T> void applyParameters(TypedQuery<T> query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
