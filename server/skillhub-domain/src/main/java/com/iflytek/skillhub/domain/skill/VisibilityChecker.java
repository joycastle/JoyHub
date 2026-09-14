package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;

import java.util.Map;
import java.util.Set;

/**
 * Evaluates whether a caller may read a skill based on publication state, visibility, ownership,
 * and namespace roles.
 */
public class VisibilityChecker {
    private final SkillVisibleNamespaceRepository visibleNamespaceRepository;
    private final NamespaceRepository namespaceRepository;

    /** Kept for domain unit tests and callers that do not persist sharing grants. */
    public VisibilityChecker() {
        this(null, null);
    }

    public VisibilityChecker(SkillVisibleNamespaceRepository visibleNamespaceRepository) {
        this(visibleNamespaceRepository, null);
    }

    public VisibilityChecker(SkillVisibleNamespaceRepository visibleNamespaceRepository,
                             NamespaceRepository namespaceRepository) {
        this.visibleNamespaceRepository = visibleNamespaceRepository;
        this.namespaceRepository = namespaceRepository;
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        return canAccess(skill, currentUserId, userNamespaceRoles, Set.of());
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles, Set<String> platformRoles) {
        Map<Long, NamespaceRole> roles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (isSuperAdmin(platformRoles)) {
            return true;
        }
        if (skill.isHidden()) {
            return isOwner(skill, currentUserId) || isAdminOrAbove(roles.get(skill.getNamespaceId()));
        }
        if (skill.getLatestVersionId() == null) {
            return isOwner(skill, currentUserId);
        }
        if (hasDepartmentRestriction(skill)) {
            return isOwner(skill, currentUserId)
                    || isAdminOrAbove(roles.get(skill.getNamespaceId()))
                    || (skill.getVisibility() != SkillVisibility.PRIVATE
                    && isNonGlobalMaintenanceNamespace(skill)
                    && roles.containsKey(skill.getNamespaceId()))
                    || hasSharedDepartmentAccess(skill, roles);
        }
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> roles.containsKey(skill.getNamespaceId());
            case PRIVATE -> isOwner(skill, currentUserId) || isAdminOrAbove(roles.get(skill.getNamespaceId()));
        };
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }

    private boolean hasSharedDepartmentAccess(Skill skill, Map<Long, NamespaceRole> userNamespaceRoles) {
        if (userNamespaceRoles == null || userNamespaceRoles.isEmpty()) {
            return false;
        }
        if (visibleNamespaceRepository == null || skill.getId() == null) {
            return false;
        }
        return visibleNamespaceRepository.findNamespaceIdsBySkillId(skill.getId()).stream()
                .anyMatch(userNamespaceRoles::containsKey);
    }

    private boolean hasExplicitDepartmentAccess(Skill skill) {
        return visibleNamespaceRepository != null
                && skill.getId() != null
                && !visibleNamespaceRepository.findNamespaceIdsBySkillId(skill.getId()).isEmpty();
    }

    private boolean hasDepartmentRestriction(Skill skill) {
        return skill.isSharingRestricted() || hasExplicitDepartmentAccess(skill);
    }

    private boolean isNonGlobalMaintenanceNamespace(Skill skill) {
        if (namespaceRepository == null || skill.getNamespaceId() == null) {
            return true;
        }
        return namespaceRepository.findById(skill.getNamespaceId())
                .map(namespace -> namespace.getType() != NamespaceType.GLOBAL)
                .orElse(true);
    }
}
