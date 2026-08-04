package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data adapter for the Catalog persistence port. */
@Repository
public interface CatalogResourceJpaRepository
        extends JpaRepository<CatalogResource, Long>, CatalogResourceRepository {

    Optional<CatalogResource> findBySlug(String slug);

    Optional<CatalogResource> findBySourceKey(String sourceKey);

    List<CatalogResource> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    @Override
    default List<CatalogResource> findByOwnerId(String ownerId) {
        return findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }
}
