package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.ResourceActionResponse;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common lifecycle facade for every owner-managed resource.
 *
 * <p>Source aggregates keep their own invariants, while this service exposes one
 * action vocabulary to the Web resource workspace.</p>
 */
@Service
public class ResourceLifecycleAppService {
    private final CatalogResourceRepository catalogRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final GovernanceWorkflowAppService governanceWorkflowAppService;
    private final CatalogResourceCommandAppService catalogCommandAppService;
    private final CatalogDeploymentLifecycleAppService catalogDeploymentLifecycleAppService;

    public ResourceLifecycleAppService(CatalogResourceRepository catalogRepository,
                                       SkillRepository skillRepository,
                                       NamespaceRepository namespaceRepository,
                                       GovernanceWorkflowAppService governanceWorkflowAppService,
                                       CatalogResourceCommandAppService catalogCommandAppService,
                                       CatalogDeploymentLifecycleAppService catalogDeploymentLifecycleAppService) {
        this.catalogRepository = catalogRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.governanceWorkflowAppService = governanceWorkflowAppService;
        this.catalogCommandAppService = catalogCommandAppService;
        this.catalogDeploymentLifecycleAppService = catalogDeploymentLifecycleAppService;
    }

    @Transactional
    public ResourceActionResponse archive(String resourceId,
                                          String userId,
                                          Map<Long, NamespaceRole> namespaceRoles,
                                          Set<String> platformRoles,
                                          AuditRequestContext auditContext) {
        ResourceReference reference = ResourceReference.parse(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            SkillLifecycleMutationResponse result = governanceWorkflowAppService.archiveSkill(
                    skillNamespace(reference),
                    skillSlug(reference),
                    new AdminSkillActionRequest(null),
                    userId,
                    namespaceRoles,
                    auditContext);
            return new ResourceActionResponse(resourceId, "ARCHIVE", result.status());
        }
        CatalogResourceDetailResponse result = catalogCommandAppService.archive(
                catalogSlug(reference), catalogViewer(userId, namespaceRoles, platformRoles));
        return new ResourceActionResponse(resourceId, "ARCHIVE", result.status());
    }

    @Transactional
    public ResourceActionResponse unarchive(String resourceId,
                                            String userId,
                                            Map<Long, NamespaceRole> namespaceRoles,
                                            Set<String> platformRoles,
                                            AuditRequestContext auditContext) {
        ResourceReference reference = ResourceReference.parse(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            SkillLifecycleMutationResponse result = governanceWorkflowAppService.unarchiveSkill(
                    skillNamespace(reference),
                    skillSlug(reference),
                    userId,
                    namespaceRoles,
                    auditContext);
            return new ResourceActionResponse(resourceId, "UNARCHIVE", result.status());
        }
        CatalogResourceDetailResponse result = catalogCommandAppService.unarchive(
                catalogSlug(reference), catalogViewer(userId, namespaceRoles, platformRoles));
        return new ResourceActionResponse(resourceId, "UNARCHIVE", result.status());
    }

    @Transactional
    public ResourceActionResponse publish(String resourceId,
                                          String version,
                                          String userId,
                                          Map<Long, NamespaceRole> namespaceRoles,
                                          Set<String> platformRoles,
                                          AuditRequestContext auditContext) {
        ResourceReference reference = ResourceReference.parse(resourceId);
        requireCatalog(reference, "PUBLISH");
        CatalogResourceDetailResponse result = catalogDeploymentLifecycleAppService.publish(
                catalogSlug(reference), version, catalogViewer(userId, namespaceRoles, platformRoles), auditContext);
        return new ResourceActionResponse(resourceId, "PUBLISH", result.status());
    }

    @Transactional
    public ResourceActionResponse offline(String resourceId,
                                          String userId,
                                          Map<Long, NamespaceRole> namespaceRoles,
                                          Set<String> platformRoles,
                                          AuditRequestContext auditContext) {
        ResourceReference reference = ResourceReference.parse(resourceId);
        requireCatalog(reference, "OFFLINE");
        CatalogResourceDetailResponse result = catalogDeploymentLifecycleAppService.takeOffline(
                catalogSlug(reference), catalogViewer(userId, namespaceRoles, platformRoles), auditContext);
        return new ResourceActionResponse(resourceId, "OFFLINE", result.status());
    }

    private void requireCatalog(ResourceReference reference, String action) {
        if (!"CATALOG".equals(reference.sourceType())) {
            throw CatalogDomainException.badRequest("error.resource.action.unsupported", action, reference.sourceType());
        }
    }

    private String catalogSlug(ResourceReference reference) {
        return catalogRepository.findById(reference.sourceId())
                .map(CatalogResource::getSlug)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", reference.sourceId()));
    }

    private CatalogViewer catalogViewer(String userId,
                                       Map<Long, NamespaceRole> namespaceRoles,
                                       Set<String> platformRoles) {
        return new CatalogViewer(
                userId,
                namespaceRoles != null ? namespaceRoles : Map.of(),
                platformRoles != null ? platformRoles : Set.of());
    }

    /* Resource IDs deliberately stay opaque to callers; these helpers resolve the
       stable source ID to the existing skill URL coordinate used by lifecycle APIs. */
    private Skill skill(ResourceReference reference) {
        return skillRepository.findById(reference.sourceId())
                .orElseThrow(() -> CatalogDomainException.notFound("error.skill.notFound", reference.sourceId()));
    }

    private String skillNamespace(ResourceReference reference) {
        return namespaceRepository.findById(skill(reference).getNamespaceId())
                .map(namespace -> namespace.getSlug())
                .orElseThrow(() -> CatalogDomainException.notFound("error.namespace.id.notFound", reference.sourceId()));
    }

    private String skillSlug(ResourceReference reference) {
        return skill(reference).getSlug();
    }
}
