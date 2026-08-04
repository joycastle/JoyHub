package com.iflytek.skillhub.catalog.domain;

import java.util.Set;

/** Pure authorization policy; identity and department membership are supplied by adapters. */
public class CatalogResourcePolicy {

    public boolean canView(CatalogResource resource,
                           String viewerId,
                           Set<Long> viewerNamespaceIds,
                           boolean superAdmin) {
        if (superAdmin || isOwner(resource, viewerId)) {
            return true;
        }
        if (resource.getStatus() != CatalogResourceStatus.PUBLISHED) {
            return false;
        }
        if (resource.getVisibilityScope() == CatalogVisibilityScope.COMPANY) {
            return true;
        }
        Set<Long> memberships = viewerNamespaceIds != null ? viewerNamespaceIds : Set.of();
        return resource.getVisibleNamespaceIds().stream().anyMatch(memberships::contains);
    }

    public boolean canManage(CatalogResource resource, String actorId, boolean superAdmin) {
        return superAdmin || isOwner(resource, actorId);
    }

    public void requireManage(CatalogResource resource, String actorId, boolean superAdmin) {
        if (!canManage(resource, actorId, superAdmin)) {
            throw CatalogDomainException.forbidden("error.catalog.manage.forbidden");
        }
    }

    private boolean isOwner(CatalogResource resource, String actorId) {
        return actorId != null && actorId.equals(resource.getOwnerId());
    }
}
