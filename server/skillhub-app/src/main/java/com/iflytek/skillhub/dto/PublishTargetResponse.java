package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.Set;

/** One namespace that the current user may publish any supported resource type into. */
public record PublishTargetResponse(
        Long id,
        String slug,
        String displayName,
        NamespaceRole currentUserRole,
        Set<String> supportedResourceTypes
) {
    public static PublishTargetResponse from(Namespace namespace, NamespaceRole role) {
        return new PublishTargetResponse(
                namespace.getId(),
                namespace.getSlug(),
                namespace.getDisplayName(),
                role,
                Set.of("SKILL", "TOOL", "AGENT"));
    }
}
