package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceDraft;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceService;
import com.iflytek.skillhub.catalog.domain.CatalogResourceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.CatalogResourceRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog commands plus cross-context ID validation. */
@Service
public class CatalogResourceCommandAppService {
    private final CatalogResourceService resourceService;
    private final CatalogResourceRepository resourceRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final UserAccountRepository userAccountRepository;
    private final CatalogResourceProjectionAssembler assembler;

    public CatalogResourceCommandAppService(CatalogResourceService resourceService,
                                            CatalogResourceRepository resourceRepository,
                                            NamespaceRepository namespaceRepository,
                                            SkillRepository skillRepository,
                                            UserAccountRepository userAccountRepository,
                                            CatalogResourceProjectionAssembler assembler) {
        this.resourceService = resourceService;
        this.resourceRepository = resourceRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.userAccountRepository = userAccountRepository;
        this.assembler = assembler;
    }

    @Transactional
    public CatalogResourceDetailResponse create(CatalogResourceRequest request, CatalogViewer viewer) {
        CatalogResourceDraft draft = validateAndMap(request, null, null, viewer);
        CatalogResource existing = resourceRepository.findBySlug(draft.slug()).orElse(null);
        if (existing != null
                && existing.getStatus() == CatalogResourceStatus.DRAFT
                && existing.getOwnerId().equals(viewer.userId())) {
            CatalogResource resumed = resourceService.update(
                    existing.getSlug(), draft, viewer.userId(), viewer.superAdmin());
            if (request.publish()) {
                resumed = resourceService.publish(existing.getSlug(), viewer.userId(), viewer.superAdmin());
            }
            return assembler.detail(resumed, viewer);
        }
        CatalogResource resource = resourceService.create(draft, viewer.userId(), request.publish());
        return assembler.detail(resource, viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse update(
            String slug,
            CatalogResourceRequest request,
            CatalogViewer viewer) {
        CatalogResource existing = resourceService.requireBySlug(slug);
        CatalogResourceDraft draft = validateAndMap(request, existing.getId(), existing.getSlug(), viewer);
        CatalogResource resource = resourceService.update(
                slug,
                draft,
                viewer.userId(),
                viewer.superAdmin()
        );
        if (request.publish()) {
            resource = resourceService.publish(slug, viewer.userId(), viewer.superAdmin());
        }
        return assembler.detail(resource, viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse publish(String slug, CatalogViewer viewer) {
        assertPublishTargetAccess(slug, viewer);
        return assembler.detail(resourceService.publish(
                slug,
                viewer.userId(),
                viewer.superAdmin()), viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse takeOffline(String slug, CatalogViewer viewer) {
        return assembler.detail(resourceService.takeOffline(
                slug,
                viewer.userId(),
                viewer.superAdmin()), viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse archive(String slug, CatalogViewer viewer) {
        return assembler.detail(resourceService.archive(
                slug,
                viewer.userId(),
                viewer.superAdmin()), viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse unarchive(String slug, CatalogViewer viewer) {
        return assembler.detail(resourceService.unarchive(
                slug,
                viewer.userId(),
                viewer.superAdmin()), viewer);
    }

    @Transactional
    public CatalogResourceDetailResponse transfer(
            String slug,
            String newOwnerId,
            CatalogViewer viewer) {
        userAccountRepository.findById(newOwnerId)
                .filter(user -> user.isActive())
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.owner.notFound", newOwnerId));
        CatalogResource resource = resourceService.transfer(
                slug,
                newOwnerId,
                viewer.userId(),
                viewer.superAdmin()
        );
        return assembler.detail(resource, new CatalogViewer(
                newOwnerId,
                viewer.namespaceRoles(),
                viewer.platformRoles()
        ));
    }

    private CatalogResourceDraft validateAndMap(
            CatalogResourceRequest request,
            Long currentResourceId,
            String existingSlug,
            CatalogViewer viewer) {
        validateAccessUrl(request.accessUrl());
        Set<Long> visibleDepartmentIds = request.visibilityScope() == CatalogVisibilityScope.DEPARTMENTS
                ? safeLongSet(request.visibleDepartmentIds()) : Set.of();
        Set<Long> departmentIds = new HashSet<>(visibleDepartmentIds);
        if (request.primaryDepartmentId() != null) {
            departmentIds.add(request.primaryDepartmentId());
        }
        validateDepartments(departmentIds);
        requirePublishTargetAccess(request.primaryDepartmentId(), viewer);
        validateResourceLinks(safeLongSet(request.relatedResourceIds()), currentResourceId);
        validateSkillLinks(safeLongSet(request.relatedSkillIds()));

        return new CatalogResourceDraft(
                existingSlug != null && (request.slug() == null || request.slug().isBlank())
                        ? existingSlug
                        : generatedSlugWhenNeeded(request),
                request.name(),
                request.summary(),
                request.kind(),
                request.icon(),
                request.accessUrl(),
                request.documentation(),
                request.version(),
                request.agentUsageBoundary(),
                request.agentInputGuide(),
                request.agentOutputGuide(),
                request.agentSupportContact(),
                safeStringSet(request.agentExamplePrompts()),
                request.primaryDepartmentId(),
                request.maintenanceStatus(),
                request.visibilityScope(),
                visibleDepartmentIds,
                safeStringSet(request.scenarios()),
                safeStringSet(request.tags()),
                safeLongSet(request.relatedResourceIds()),
                safeLongSet(request.relatedSkillIds())
        );
    }

    private String generatedSlugWhenNeeded(CatalogResourceRequest request) {
        if (request.kind() != com.iflytek.skillhub.catalog.domain.CatalogResourceKind.AGENT
                || request.slug() != null && !request.slug().isBlank()) {
            return request.slug();
        }
        return "agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void validateDepartments(Set<Long> departmentIds) {
        if (departmentIds.isEmpty()) {
            return;
        }
        List<Namespace> departments = namespaceRepository.findByIdIn(new ArrayList<>(departmentIds));
        Set<Long> activeIds = departments.stream()
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .map(Namespace::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!activeIds.containsAll(departmentIds)) {
            throw CatalogDomainException.badRequest("error.catalog.department.invalid");
        }
    }

    private void requirePublishTargetAccess(Long namespaceId, CatalogViewer viewer) {
        if (namespaceId == null) {
            throw CatalogDomainException.badRequest("error.catalog.publishTarget.required");
        }
        if (!viewer.superAdmin() && !viewer.namespaceIds().contains(namespaceId)) {
            throw CatalogDomainException.forbidden("error.catalog.publishTarget.membershipRequired");
        }
    }

    @Transactional(readOnly = true)
    public void assertPublishTargetAccess(String slug, CatalogViewer viewer) {
        requirePublishTargetAccess(resourceService.requireBySlug(slug).getPrimaryNamespaceId(), viewer);
    }

    private void validateResourceLinks(Set<Long> resourceIds, Long currentResourceId) {
        if (currentResourceId != null && resourceIds.contains(currentResourceId)) {
            throw CatalogDomainException.badRequest("error.catalog.relation.self");
        }
        for (Long resourceId : resourceIds) {
            if (resourceRepository.findById(resourceId).isEmpty()) {
                throw CatalogDomainException.badRequest("error.catalog.relation.resourceNotFound", resourceId);
            }
        }
    }

    private void validateSkillLinks(Set<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return;
        }
        Set<Long> foundIds = skillRepository.findByIdIn(new ArrayList<>(skillIds)).stream()
                .map(com.iflytek.skillhub.domain.skill.Skill::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!foundIds.containsAll(skillIds)) {
            throw CatalogDomainException.badRequest("error.catalog.relation.skillNotFound");
        }
    }

    private void validateAccessUrl(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw CatalogDomainException.badRequest("error.catalog.accessUrl.invalid");
            }
        } catch (URISyntaxException exception) {
            throw CatalogDomainException.badRequest("error.catalog.accessUrl.invalid");
        }
    }

    private static Set<Long> safeLongSet(Set<Long> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static Set<String> safeStringSet(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
