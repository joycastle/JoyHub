package com.iflytek.skillhub.catalog.domain;

import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/** Catalog aggregate command service. Cross-context validation stays in the application adapter. */
public class CatalogResourceService {
    private final CatalogResourceRepository repository;
    private final CatalogResourcePolicy policy;
    private final Clock clock;

    public CatalogResourceService(CatalogResourceRepository repository,
                                  CatalogResourcePolicy policy,
                                  Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public CatalogResource create(CatalogResourceDraft draft, String ownerId, boolean publish) {
        repository.findBySlug(draft.slug()).ifPresent(existing -> {
            throw CatalogDomainException.conflict("error.catalog.slug.exists", draft.slug());
        });
        CatalogResource resource = new CatalogResource(draft, ownerId);
        if (publish) {
            resource.publish(clock.instant());
        }
        return repository.save(resource);
    }

    @Transactional
    public CatalogResource update(String slug,
                                  CatalogResourceDraft draft,
                                  String actorId,
                                  boolean superAdmin) {
        CatalogResource resource = requireBySlug(slug);
        policy.requireManage(resource, actorId, superAdmin);
        resource.update(draft);
        return repository.save(resource);
    }

    @Transactional
    public CatalogResource publish(String slug, String actorId, boolean superAdmin) {
        CatalogResource resource = requireBySlug(slug);
        policy.requireManage(resource, actorId, superAdmin);
        resource.publish(clock.instant());
        return repository.save(resource);
    }

    @Transactional
    public CatalogResource takeOffline(String slug, String actorId, boolean superAdmin) {
        CatalogResource resource = requireBySlug(slug);
        policy.requireManage(resource, actorId, superAdmin);
        resource.takeOffline();
        return repository.save(resource);
    }

    @Transactional
    public CatalogResource transfer(String slug, String newOwnerId, String actorId, boolean superAdmin) {
        CatalogResource resource = requireBySlug(slug);
        policy.requireManage(resource, actorId, superAdmin);
        resource.transferOwnership(newOwnerId);
        return repository.save(resource);
    }

    public CatalogResource requireBySlug(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", slug));
    }
}
