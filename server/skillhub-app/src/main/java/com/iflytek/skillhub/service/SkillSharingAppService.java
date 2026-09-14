package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.service.SkillSharingService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.dto.SharingTargetResponse;
import com.iflytek.skillhub.dto.SkillSharingResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application-layer adapter for skill sharing management endpoints. */
@Service
public class SkillSharingAppService {
    private final NamespaceRepository namespaceRepository;
    private final SkillSlugResolutionService skillSlugResolutionService;
    private final SkillSharingService skillSharingService;

    public SkillSharingAppService(NamespaceRepository namespaceRepository,
                                  SkillSlugResolutionService skillSlugResolutionService,
                                  SkillSharingService skillSharingService) {
        this.namespaceRepository = namespaceRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
        this.skillSharingService = skillSharingService;
    }

    @Transactional(readOnly = true)
    public SkillSharingResponse list(String namespaceSlug,
                                     String skillSlug,
                                     String operatorUserId,
                                     Map<Long, NamespaceRole> namespaceRoles,
                                     Set<String> platformRoles) {
        Skill skill = resolveSkill(namespaceSlug, skillSlug, operatorUserId);
        List<Long> ids = skillSharingService.listDepartmentIds(skill.getId(), operatorUserId, namespaceRoles, platformRoles);
        return toResponse(ids, skill.isSharingRestricted() || !ids.isEmpty());
    }

    @Transactional
    public SkillSharingResponse replace(String namespaceSlug,
                                        String skillSlug,
                                        Set<Long> namespaceIds,
                                        String operatorUserId,
                                        Map<Long, NamespaceRole> namespaceRoles,
                                        Set<String> platformRoles,
                                        boolean restricted) {
        Skill skill = resolveSkill(namespaceSlug, skillSlug, operatorUserId);
        List<Long> ids = skillSharingService.replaceDepartments(
                skill.getId(), namespaceIds, restricted, operatorUserId, namespaceRoles, platformRoles);
        return toResponse(ids, restricted);
    }

    private Skill resolveSkill(String namespaceSlug, String skillSlug, String currentUserId) {
        String cleanNamespace = namespaceSlug != null && namespaceSlug.startsWith("@")
                ? namespaceSlug.substring(1)
                : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                        "error.namespace.slug.notFound", cleanNamespace));
        return skillSlugResolutionService.resolve(
                namespace.getId(), skillSlug, currentUserId, SkillSlugResolutionService.Preference.CURRENT_USER);
    }

    private SkillSharingResponse toResponse(List<Long> ids, boolean restricted) {
        if (ids.isEmpty()) {
            return new SkillSharingResponse(List.of(), restricted);
        }
        Map<Long, Namespace> namespaces = namespaceRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        List<SharingTargetResponse> targets = ids.stream()
                .map(namespaces::get)
                .filter(java.util.Objects::nonNull)
                .map(namespace -> new SharingTargetResponse(
                        namespace.getId(), namespace.getSlug(), namespace.getDisplayName()))
                .toList();
        return new SkillSharingResponse(targets, restricted);
    }
}
