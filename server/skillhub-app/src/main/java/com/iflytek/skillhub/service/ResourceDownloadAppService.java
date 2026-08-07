package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Common binary delivery facade for Skill bundles and Catalog artifacts. */
@Service
public class ResourceDownloadAppService {
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final CatalogResourceRepository catalogRepository;
    private final SkillDownloadService skillDownloadService;
    private final CatalogArtifactAppService catalogArtifactAppService;

    public ResourceDownloadAppService(SkillRepository skillRepository,
                                      NamespaceRepository namespaceRepository,
                                      CatalogResourceRepository catalogRepository,
                                      SkillDownloadService skillDownloadService,
                                      CatalogArtifactAppService catalogArtifactAppService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.catalogRepository = catalogRepository;
        this.skillDownloadService = skillDownloadService;
        this.catalogArtifactAppService = catalogArtifactAppService;
    }

    public ResourceDownload download(String resourceId,
                                    String userId,
                                    Map<Long, NamespaceRole> namespaceRoles,
                                    Set<String> platformRoles) {
        ResourceReference reference = ResourceReference.parse(resourceId);
        if ("SKILL".equals(reference.sourceType())) {
            Skill skill = skillRepository.findById(reference.sourceId())
                    .orElseThrow(() -> new DomainNotFoundException(
                            "error.skill.notFound", reference.sourceId()));
            String namespaceSlug = namespaceRepository.findById(skill.getNamespaceId())
                    .map(namespace -> namespace.getSlug())
                    .orElseThrow(() -> new DomainNotFoundException(
                            "error.namespace.id.notFound", skill.getNamespaceId()));
            SkillDownloadService.DownloadResult result = skillDownloadService.downloadLatest(
                    namespaceSlug, skill.getSlug(), userId,
                    namespaceRoles != null ? namespaceRoles : Map.of());
            return new ResourceDownload(result.openContent(), result.filename(), result.contentType(), result.contentLength());
        }
        if ("CATALOG".equals(reference.sourceType())) {
            CatalogResource resource = catalogRepository.findById(reference.sourceId())
                    .orElseThrow(() -> new DomainNotFoundException(
                            "error.catalog.notFound", reference.sourceId()));
            CatalogArtifactAppService.CatalogArtifactDownload result = catalogArtifactAppService.download(
                    resource.getSlug(),
                    new CatalogViewer(userId,
                            namespaceRoles != null ? namespaceRoles : Map.of(),
                            platformRoles != null ? platformRoles : Set.of()));
            return new ResourceDownload(result.stream(), result.filename(), result.contentType(), result.size());
        }
        throw new com.iflytek.skillhub.exception.BadRequestException(
                "error.resource.reference.invalid", resourceId);
    }

    public record ResourceDownload(InputStream stream, String filename, String contentType, long size) {
    }
}
