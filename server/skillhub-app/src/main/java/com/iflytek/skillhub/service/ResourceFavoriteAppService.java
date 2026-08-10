package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.SkillStarService;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source-agnostic favorites facade. Skills retain their existing star aggregate;
 * static Catalog resources use the shared resource_favorite table.
 */
@Service
public class ResourceFavoriteAppService {
    private final JdbcTemplate jdbcTemplate;
    private final SkillRepository skillRepository;
    private final CatalogResourceRepository catalogRepository;
    private final SkillStarService skillStarService;

    public ResourceFavoriteAppService(JdbcTemplate jdbcTemplate,
                                      SkillRepository skillRepository,
                                      CatalogResourceRepository catalogRepository,
                                      SkillStarService skillStarService) {
        this.jdbcTemplate = jdbcTemplate;
        this.skillRepository = skillRepository;
        this.catalogRepository = catalogRepository;
        this.skillStarService = skillStarService;
    }

    @Transactional
    public void favorite(String resourceId, String userId) {
        ResourceReference reference = requireExisting(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            skillStarService.star(reference.sourceId(), userId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO resource_favorite (source_type, source_id, user_id)
                VALUES ('CATALOG', ?, ?)
                ON CONFLICT (source_type, source_id, user_id) DO NOTHING
                """, reference.sourceId(), userId);
    }

    @Transactional
    public void unfavorite(String resourceId, String userId) {
        ResourceReference reference = requireExisting(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            skillStarService.unstar(reference.sourceId(), userId);
            return;
        }
        jdbcTemplate.update("DELETE FROM resource_favorite WHERE source_type = 'CATALOG' AND source_id = ? AND user_id = ?",
                reference.sourceId(), userId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(String resourceId, String userId) {
        ResourceReference reference = requireExisting(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            return skillStarService.isStarred(reference.sourceId(), userId);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resource_favorite WHERE source_type = 'CATALOG' AND source_id = ? AND user_id = ?",
                Integer.class, reference.sourceId(), userId);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public int count(String sourceType, Long sourceId) {
        if ("SKILL".equalsIgnoreCase(sourceType)) {
            return skillRepository.findById(sourceId).map(skill -> skill.getStarCount() != null ? skill.getStarCount() : 0).orElse(0);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resource_favorite WHERE source_type = 'CATALOG' AND source_id = ?",
                Integer.class, sourceId);
        return count != null ? count : 0;
    }

    /** Returns canonical references for every resource favorited by one user. */
    @Transactional(readOnly = true)
    public Set<String> findFavoriteResourceIds(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT 'SKILL:' || skill_id::text
                  FROM skill_star
                 WHERE user_id = ?
                UNION ALL
                SELECT 'CATALOG:' || source_id::text
                  FROM resource_favorite
                 WHERE source_type = 'CATALOG' AND user_id = ?
                """, String.class, userId, userId));
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
}
