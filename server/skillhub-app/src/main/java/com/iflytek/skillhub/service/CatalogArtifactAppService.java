package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Binary artifact workflow kept outside Catalog domain and storage implementations. */
@Service
public class CatalogArtifactAppService {
    private static final long MAX_ARTIFACT_SIZE = 100L * 1024L * 1024L;

    private final CatalogResourceRepository repository;
    private final CatalogResourcePolicy policy;
    private final ObjectStorageService objectStorageService;
    private final CatalogResourceProjectionAssembler assembler;

    public CatalogArtifactAppService(CatalogResourceRepository repository,
                                     CatalogResourcePolicy policy,
                                     ObjectStorageService objectStorageService,
                                     CatalogResourceProjectionAssembler assembler) {
        this.repository = repository;
        this.policy = policy;
        this.objectStorageService = objectStorageService;
        this.assembler = assembler;
    }

    @Transactional
    public com.iflytek.skillhub.dto.CatalogResourceDetailResponse upload(
            String slug,
            MultipartFile file,
            CatalogViewer viewer) {
        CatalogResource resource = requireResource(slug);
        policy.requireManage(resource, viewer.userId(), viewer.superAdmin());
        validate(file);

        String originalFilename = safeFilename(file.getOriginalFilename());
        String storageKey = "catalog/" + resource.getId() + "/" + UUID.randomUUID() + "-" + originalFilename;
        String oldStorageKey = resource.getArtifactStorageKey();
        try (InputStream input = file.getInputStream()) {
            objectStorageService.putObject(
                    storageKey,
                    input,
                    file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/zip"
            );
            resource.attachArtifact(storageKey, originalFilename, file.getContentType(), file.getSize());
            repository.save(resource);
        } catch (IOException exception) {
            if (objectStorageService.exists(storageKey)) {
                objectStorageService.deleteObject(storageKey);
            }
            throw CatalogDomainException.badRequest("error.catalog.artifact.readFailed");
        } catch (RuntimeException exception) {
            if (objectStorageService.exists(storageKey)) {
                objectStorageService.deleteObject(storageKey);
            }
            throw exception;
        }
        if (oldStorageKey != null && !oldStorageKey.equals(storageKey)) {
            objectStorageService.deleteObject(oldStorageKey);
        }
        return assembler.detail(resource, viewer);
    }

    @Transactional(readOnly = true)
    public CatalogArtifactDownload download(String slug, CatalogViewer viewer) {
        CatalogResource resource = requireResource(slug);
        if (!policy.canView(resource, viewer.userId(), viewer.namespaceIds(), viewer.superAdmin())) {
            throw CatalogDomainException.notFound("error.catalog.notFound", slug);
        }
        if (!resource.hasArtifact()) {
            throw CatalogDomainException.notFound("error.catalog.artifact.notFound", slug);
        }
        return new CatalogArtifactDownload(
                objectStorageService.getObject(resource.getArtifactStorageKey()),
                resource.getArtifactFilename(),
                resource.getArtifactContentType(),
                resource.getArtifactSize() != null ? resource.getArtifactSize() : 0L
        );
    }

    private CatalogResource requireResource(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", slug));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CatalogDomainException.badRequest("error.catalog.artifact.required");
        }
        if (file.getSize() > MAX_ARTIFACT_SIZE) {
            throw CatalogDomainException.badRequest("error.catalog.artifact.tooLarge", MAX_ARTIFACT_SIZE);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw CatalogDomainException.badRequest("error.catalog.artifact.zipOnly");
        }
    }

    private String safeFilename(String filename) {
        String value = filename != null ? filename.replace('\\', '/') : "artifact.zip";
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "-");
        return value.isBlank() ? "artifact.zip" : value;
    }

    public record CatalogArtifactDownload(
            InputStream stream,
            String filename,
            String contentType,
            long size
    ) {
    }
}
