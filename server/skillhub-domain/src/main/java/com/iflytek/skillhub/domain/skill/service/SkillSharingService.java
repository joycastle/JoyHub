package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibleNamespace;
import com.iflytek.skillhub.domain.skill.SkillVisibleNamespaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages explicit department access grants without changing skill ownership. */
@Service
public class SkillSharingService {
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVisibleNamespaceRepository visibleNamespaceRepository;

    public SkillSharingService(SkillRepository skillRepository,
                               NamespaceRepository namespaceRepository,
                               SkillVisibleNamespaceRepository visibleNamespaceRepository) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.visibleNamespaceRepository = visibleNamespaceRepository;
    }

    @Transactional(readOnly = true)
    public Skill requireManageableSkill(Long skillId,
                                        String operatorUserId,
                                        Map<Long, NamespaceRole> namespaceRoles,
                                        Set<String> platformRoles) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        assertCanManage(skill, operatorUserId, namespaceRoles, platformRoles);
        return skill;
    }

    @Transactional
    public List<Long> replaceDepartments(Long skillId,
                                         Set<Long> requestedNamespaceIds,
                                         String operatorUserId,
                                         Map<Long, NamespaceRole> namespaceRoles,
                                         Set<String> platformRoles) {
        return replaceDepartments(skillId, requestedNamespaceIds, true, operatorUserId, namespaceRoles, platformRoles);
    }

    @Transactional
    public List<Long> replaceDepartments(Long skillId,
                                         Set<Long> requestedNamespaceIds,
                                         boolean restricted,
                                         String operatorUserId,
                                         Map<Long, NamespaceRole> namespaceRoles,
                                         Set<String> platformRoles) {
        Skill skill = requireManageableSkill(skillId, operatorUserId, namespaceRoles, platformRoles);
        Set<Long> normalizedIds = requestedNamespaceIds == null
                ? Set.of()
                : requestedNamespaceIds.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());

        List<Namespace> namespaces = normalizedIds.isEmpty()
                ? List.of()
                : namespaceRepository.findByIdIn(normalizedIds.stream().toList());
        Set<Long> validIds = namespaces.stream()
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .filter(namespace -> namespace.getType() != NamespaceType.GLOBAL)
                .map(Namespace::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!validIds.containsAll(normalizedIds)) {
            throw new DomainBadRequestException("error.skill.sharing.department.invalid");
        }

        visibleNamespaceRepository.deleteBySkillId(skill.getId());
        skill.setSharingRestricted(restricted);
        skillRepository.save(skill);
        if (!restricted) {
            return List.of();
        }
        validIds.stream().sorted().map(namespaceId -> new SkillVisibleNamespace(skill.getId(), namespaceId))
                .forEach(visibleNamespaceRepository::save);
        return validIds.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Long> listDepartmentIds(Long skillId,
                                        String operatorUserId,
                                        Map<Long, NamespaceRole> namespaceRoles,
                                        Set<String> platformRoles) {
        Skill skill = requireManageableSkill(skillId, operatorUserId, namespaceRoles, platformRoles);
        return visibleNamespaceRepository.findNamespaceIdsBySkillId(skill.getId()).stream()
                .distinct()
                .sorted()
                .toList();
    }

    public void assertCanManage(Skill skill,
                                String operatorUserId,
                                Map<Long, NamespaceRole> namespaceRoles,
                                Set<String> platformRoles) {
        if (operatorUserId == null || operatorUserId.isBlank()) {
            throw new DomainForbiddenException("error.skill.sharing.manage.required");
        }
        Set<String> roles = platformRoles == null ? Set.of() : platformRoles;
        Map<Long, NamespaceRole> memberships = namespaceRoles == null ? Map.of() : namespaceRoles;
        NamespaceRole namespaceRole = memberships.get(skill.getNamespaceId());
        boolean ownerOrAdmin = operatorUserId.equals(skill.getOwnerId())
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
        if (!ownerOrAdmin && !roles.contains("SUPER_ADMIN")) {
            throw new DomainForbiddenException("error.skill.sharing.manage.required");
        }
    }
}
