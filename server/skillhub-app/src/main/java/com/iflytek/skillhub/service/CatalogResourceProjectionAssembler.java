package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.CatalogDepartmentResponse;
import com.iflytek.skillhub.dto.CatalogOwnerResponse;
import com.iflytek.skillhub.dto.CatalogRelatedSkillResponse;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Assembles Catalog read models across the Catalog, Organization, Skill, and Identity contexts. */
@Component
public class CatalogResourceProjectionAssembler {
    private final CatalogResourceRepository catalogRepository;
    private final CatalogResourcePolicy catalogPolicy;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final VisibilityChecker skillVisibilityChecker;
    private final UserAccountRepository userAccountRepository;

    public CatalogResourceProjectionAssembler(CatalogResourceRepository catalogRepository,
                                              CatalogResourcePolicy catalogPolicy,
                                              NamespaceRepository namespaceRepository,
                                              SkillRepository skillRepository,
                                              VisibilityChecker skillVisibilityChecker,
                                              UserAccountRepository userAccountRepository) {
        this.catalogRepository = catalogRepository;
        this.catalogPolicy = catalogPolicy;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVisibilityChecker = skillVisibilityChecker;
        this.userAccountRepository = userAccountRepository;
    }

    public List<CatalogResourceSummaryResponse> summaries(List<CatalogResource> resources) {
        ProjectionContext context = contextFor(resources);
        return resources.stream().map(resource -> summary(resource, context)).toList();
    }

    public CatalogResourceDetailResponse detail(CatalogResource resource, CatalogViewer viewer) {
        List<CatalogResource> relatedResources = resource.getRelatedResourceIds().stream()
                .map(catalogRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(related -> catalogPolicy.canView(
                        related,
                        viewer.userId(),
                        viewer.namespaceIds(),
                        viewer.superAdmin()))
                .sorted(Comparator.comparing(CatalogResource::getName))
                .toList();
        List<CatalogResource> resourcesForContext = new ArrayList<>(relatedResources);
        resourcesForContext.add(resource);
        ProjectionContext context = contextFor(resourcesForContext);

        List<CatalogRelatedSkillResponse> relatedSkills = relatedSkills(resource, viewer, context.namespaces());
        List<CatalogDepartmentResponse> visibleDepartments = resource.getVisibleNamespaceIds().stream()
                .map(context.namespaces()::get)
                .filter(Objects::nonNull)
                .map(this::department)
                .sorted(Comparator.comparing(CatalogDepartmentResponse::name))
                .toList();

        return new CatalogResourceDetailResponse(
                resource.getId(),
                resource.getSlug(),
                resource.getName(),
                resource.getSummary(),
                resource.getKind().name(),
                resource.getIcon(),
                resource.getAccessUrl(),
                resource.getDocumentation(),
                resource.getVersion(),
                resource.getAgentUsageBoundary(),
                resource.getAgentInputGuide(),
                resource.getAgentOutputGuide(),
                resource.getAgentSupportContact(),
                resource.getAgentExamplePrompts(),
                department(context.namespaces().get(resource.getPrimaryNamespaceId())),
                owner(context.users().get(resource.getOwnerId()), resource.getOwnerId()),
                resource.getStatus().name(),
                resource.getMaintenanceStatus().name(),
                resource.getVisibilityScope().name(),
                visibleDepartments,
                resource.getScenarios(),
                resource.getTags(),
                relatedResources.stream().map(related -> summary(related, context)).toList(),
                relatedSkills,
                resource.hasArtifact(),
                resource.getArtifactFilename(),
                resource.getArtifactSize(),
                catalogPolicy.canManage(resource, viewer.userId(), viewer.superAdmin()),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                resource.getPublishedAt()
        );
    }

    private List<CatalogRelatedSkillResponse> relatedSkills(
            CatalogResource resource,
            CatalogViewer viewer,
            Map<Long, Namespace> knownNamespaces) {
        if (resource.getRelatedSkillIds().isEmpty()) {
            return List.of();
        }
        List<Skill> skills = skillRepository.findByIdIn(new ArrayList<>(resource.getRelatedSkillIds()));
        Map<Long, Namespace> namespaces = new HashMap<>(knownNamespaces);
        List<Long> missingNamespaceIds = skills.stream()
                .map(Skill::getNamespaceId)
                .filter(id -> !namespaces.containsKey(id))
                .distinct()
                .toList();
        namespaceRepository.findByIdIn(missingNamespaceIds).forEach(namespace -> namespaces.put(namespace.getId(), namespace));

        return skills.stream()
                .filter(skill -> skillVisibilityChecker.canAccess(
                        skill,
                        viewer.userId(),
                        viewer.namespaceRoles(),
                        viewer.platformRoles()))
                .map(skill -> {
                    Namespace namespace = namespaces.get(skill.getNamespaceId());
                    return new CatalogRelatedSkillResponse(
                            skill.getId(),
                            namespace != null ? namespace.getSlug() : "unknown",
                            skill.getSlug(),
                            skill.getDisplayName() != null ? skill.getDisplayName() : skill.getSlug(),
                            skill.getSummary()
                    );
                })
                .toList();
    }

    private CatalogResourceSummaryResponse summary(CatalogResource resource, ProjectionContext context) {
        return new CatalogResourceSummaryResponse(
                resource.getId(),
                resource.getSlug(),
                resource.getName(),
                resource.getSummary(),
                resource.getKind().name(),
                resource.getIcon(),
                resource.getAccessUrl(),
                resource.getVersion(),
                department(context.namespaces().get(resource.getPrimaryNamespaceId())),
                owner(context.users().get(resource.getOwnerId()), resource.getOwnerId()),
                resource.getStatus().name(),
                resource.getMaintenanceStatus().name(),
                resource.getVisibilityScope().name(),
                resource.getScenarios(),
                resource.getTags(),
                resource.hasArtifact(),
                resource.getUpdatedAt()
        );
    }

    private ProjectionContext contextFor(List<CatalogResource> resources) {
        List<Long> namespaceIds = resources.stream()
                .flatMap(resource -> {
                    java.util.stream.Stream<Long> primary = resource.getPrimaryNamespaceId() != null
                            ? java.util.stream.Stream.of(resource.getPrimaryNamespaceId())
                            : java.util.stream.Stream.empty();
                    return java.util.stream.Stream.concat(primary, resource.getVisibleNamespaceIds().stream());
                })
                .distinct()
                .toList();
        Map<Long, Namespace> namespaces = namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, namespace -> namespace));

        List<String> userIds = resources.stream().map(CatalogResource::getOwnerId).distinct().toList();
        Map<String, UserAccount> users = userAccountRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, user -> user));
        return new ProjectionContext(namespaces, users);
    }

    private CatalogDepartmentResponse department(Namespace namespace) {
        return namespace == null ? null : new CatalogDepartmentResponse(
                namespace.getId(), namespace.getSlug(), namespace.getDisplayName());
    }

    private CatalogOwnerResponse owner(UserAccount user, String fallbackId) {
        return new CatalogOwnerResponse(
                fallbackId,
                user != null ? user.getDisplayName() : fallbackId
        );
    }

    private record ProjectionContext(Map<Long, Namespace> namespaces, Map<String, UserAccount> users) {
    }
}
