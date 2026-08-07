package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.ResourceStatsResponse;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unified counters for views, uses, downloads, and favorites across resource types. */
@Service
public class ResourceStatsAppService {
    private final JdbcTemplate jdbcTemplate;
    private final SkillRepository skillRepository;
    private final CatalogResourceRepository catalogRepository;
    private final ResourceFavoriteAppService favoriteAppService;

    public ResourceStatsAppService(JdbcTemplate jdbcTemplate,
                                   SkillRepository skillRepository,
                                   CatalogResourceRepository catalogRepository,
                                   ResourceFavoriteAppService favoriteAppService) {
        this.jdbcTemplate = jdbcTemplate;
        this.skillRepository = skillRepository;
        this.catalogRepository = catalogRepository;
        this.favoriteAppService = favoriteAppService;
    }

    @Transactional(readOnly = true)
    public ResourceStatsResponse get(String resourceId, String userId) {
        ResourceReference reference = requireExisting(resourceId);
        Map<String, Object> row = jdbcTemplate.query(
                "SELECT view_count, use_count, download_count FROM resource_stat WHERE source_type = ? AND source_id = ?",
                resultSet -> resultSet.next()
                        ? Map.of(
                                "viewCount", resultSet.getLong("view_count"),
                                "useCount", resultSet.getLong("use_count"),
                                "downloadCount", resultSet.getLong("download_count"))
                        : Map.of(),
                reference.sourceType(), reference.sourceId());
        long downloadCount = "SKILL".equals(reference.sourceType())
                ? skillRepository.findById(reference.sourceId()).map(skill -> skill.getDownloadCount() == null ? 0L : skill.getDownloadCount()).orElse(0L)
                : number(row, "downloadCount");
        return new ResourceStatsResponse(
                reference.value(),
                number(row, "viewCount"),
                number(row, "useCount"),
                downloadCount,
                favoriteAppService.count(reference.sourceType(), reference.sourceId()),
                userId != null && !userId.isBlank() && favoriteAppService.isFavorited(reference.value(), userId));
    }

    @Transactional
    public void recordView(String resourceId) {
        increment(resourceId, "view_count");
    }

    @Transactional
    public void recordUse(String resourceId) {
        increment(resourceId, "use_count");
    }

    @Transactional
    public void recordDownload(String resourceId) {
        ResourceReference reference = requireExisting(resourceId);
        if ("CATALOG".equals(reference.sourceType())) {
            increment(reference, "download_count");
        }
    }

    private void increment(String resourceId, String column) {
        increment(requireExisting(resourceId), column);
    }

    private void increment(ResourceReference reference, String column) {
        if (!SetOfColumns.ALLOWED.contains(column)) {
            throw new IllegalArgumentException("Unsupported resource counter: " + column);
        }
        jdbcTemplate.update("""
                INSERT INTO resource_stat (source_type, source_id, %s)
                VALUES (?, ?, 1)
                ON CONFLICT (source_type, source_id) DO UPDATE
                SET %s = resource_stat.%s + 1, updated_at = CURRENT_TIMESTAMP
                """.formatted(column, column, column), reference.sourceType(), reference.sourceId());
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private ResourceReference requireExisting(String rawReference) {
        ResourceReference reference = ResourceReference.parse(rawReference);
        if ("SKILL".equals(reference.sourceType())) {
            if (skillRepository.findById(reference.sourceId()).isEmpty()) {
                throw new DomainNotFoundException("skill.not_found", reference.sourceId());
            }
        } else if ("CATALOG".equals(reference.sourceType())) {
            if (catalogRepository.findById(reference.sourceId()).isEmpty()) {
                throw new DomainNotFoundException("error.catalog.notFound", reference.sourceId());
            }
        } else {
            throw new com.iflytek.skillhub.exception.BadRequestException(
                    "error.resource.reference.invalid", rawReference);
        }
        return reference;
    }

    private static final class SetOfColumns {
        private static final java.util.Set<String> ALLOWED = java.util.Set.of("view_count", "use_count", "download_count");

        private SetOfColumns() {
        }
    }
}
