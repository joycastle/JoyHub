package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.repository.MySkillQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The single owner-facing resource read model. Skill and Catalog remain type-specific sources,
 * but the Web resource workspace no longer needs to know about two list APIs.
 */
@Service
public class ResourceAppService {
    private static final String SOURCE_SKILL = "SKILL";
    private static final String SOURCE_CATALOG = "CATALOG";

    private final SkillRepository skillRepository;
    private final CatalogResourceRepository catalogResourceRepository;
    private final MySkillQueryRepository mySkillQueryRepository;
    private final CatalogResourceProjectionAssembler catalogProjectionAssembler;

    public ResourceAppService(SkillRepository skillRepository,
                              CatalogResourceRepository catalogResourceRepository,
                              MySkillQueryRepository mySkillQueryRepository,
                              CatalogResourceProjectionAssembler catalogProjectionAssembler) {
        this.skillRepository = skillRepository;
        this.catalogResourceRepository = catalogResourceRepository;
        this.mySkillQueryRepository = mySkillQueryRepository;
        this.catalogProjectionAssembler = catalogProjectionAssembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceSummaryResponse> listMine(String userId,
                                                           int page,
                                                           int size,
                                                           String kind,
                                                           String keyword) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        String normalizedKind = normalize(kind);
        String normalizedKeyword = normalizeKeyword(keyword);

        List<ResourceSummaryResponse> resources = new ArrayList<>();
        if (normalizedKind == null || SOURCE_SKILL.equals(normalizedKind)) {
            List<Skill> skills = skillRepository.findByOwnerId(userId).stream()
                    .filter(skill -> matches(skill, normalizedKeyword))
                    .toList();
            resources.addAll(toSkillResources(skills, userId));
        }
        if (normalizedKind == null || !SOURCE_SKILL.equals(normalizedKind)) {
            List<CatalogResource> catalogResources = catalogResourceRepository.findByOwnerId(userId).stream()
                    .filter(resource -> normalizedKind == null || normalizedKind.equals(resource.getKind().name()))
                    .filter(resource -> matches(resource, normalizedKeyword))
                    .toList();
            resources.addAll(toCatalogResources(catalogResources));
        }

        resources.sort(Comparator.comparing(ResourceSummaryResponse::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(normalizedPage * normalizedSize, resources.size());
        int to = Math.min(from + normalizedSize, resources.size());
        return new PageResponse<>(resources.subList(from, to), resources.size(), normalizedPage, normalizedSize);
    }

    private List<ResourceSummaryResponse> toSkillResources(List<Skill> skills, String userId) {
        if (skills.isEmpty()) {
            return List.of();
        }
        List<SkillSummaryResponse> summaries = mySkillQueryRepository.getSkillSummaries(skills, userId);
        return summaries.stream().map(this::toSkillResource).toList();
    }

    private List<ResourceSummaryResponse> toCatalogResources(List<CatalogResource> resources) {
        if (resources.isEmpty()) {
            return List.of();
        }
        return catalogProjectionAssembler.summaries(resources).stream()
                .map(summary -> new ResourceSummaryResponse(
                        resourceId(SOURCE_CATALOG, summary.id()),
                        SOURCE_CATALOG,
                        summary.id(),
                        summary.kind(),
                        summary.slug(),
                        summary.name(),
                        summary.summary(),
                        summary.department() != null ? summary.department().slug() : null,
                        summary.status(),
                        summary.version(),
                        null,
                        summary.visibilityScope(),
                        0L,
                        0,
                        0,
                        true,
                        summary.updatedAt()))
                .toList();
    }

    private ResourceSummaryResponse toSkillResource(SkillSummaryResponse summary) {
        SkillLifecycleVersionResponse version = summary.headlineVersion();
        return new ResourceSummaryResponse(
                resourceId(SOURCE_SKILL, summary.id()),
                SOURCE_SKILL,
                summary.id(),
                SOURCE_SKILL,
                summary.slug(),
                summary.displayName(),
                summary.summary(),
                summary.namespace(),
                summary.status(),
                version != null ? version.version() : null,
                version != null ? version.status() : null,
                summary.visibility(),
                summary.downloadCount(),
                summary.starCount(),
                summary.ratingCount(),
                true,
                summary.updatedAt());
    }

    private boolean matches(Skill skill, String keyword) {
        if (keyword == null) {
            return true;
        }
        return contains(skill.getDisplayName(), keyword)
                || contains(skill.getSlug(), keyword)
                || contains(skill.getSummary(), keyword);
    }

    private boolean matches(CatalogResource resource, String keyword) {
        if (keyword == null) {
            return true;
        }
        return contains(resource.getName(), keyword)
                || contains(resource.getSlug(), keyword)
                || contains(resource.getSummary(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String resourceId(String sourceType, Long sourceId) {
        return sourceType.toLowerCase(Locale.ROOT) + ":" + sourceId;
    }
}
