package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.Map;
import java.util.Set;

/** Auth adapter value passed into Catalog application services. */
public record CatalogViewer(
        String userId,
        Map<Long, NamespaceRole> namespaceRoles,
        Set<String> platformRoles
) {
    public CatalogViewer {
        namespaceRoles = namespaceRoles == null ? Map.of() : Map.copyOf(namespaceRoles);
        platformRoles = platformRoles == null ? Set.of() : Set.copyOf(platformRoles);
    }

    public Set<Long> namespaceIds() {
        return namespaceRoles.keySet();
    }

    public boolean superAdmin() {
        return platformRoles.contains("SUPER_ADMIN");
    }
}
