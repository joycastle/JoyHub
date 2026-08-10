package com.iflytek.skillhub.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistence port owned by the Catalog bounded context. */
public interface CatalogResourceRepository {
    Optional<CatalogResource> findById(Long id);
    Optional<CatalogResource> findBySlug(String slug);
    Optional<CatalogResource> findBySourceKey(String sourceKey);
    List<CatalogResource> findByIdIn(Set<Long> ids);
    List<CatalogResource> findAll();
    List<CatalogResource> findByOwnerId(String ownerId);
    CatalogResource save(CatalogResource resource);
}
