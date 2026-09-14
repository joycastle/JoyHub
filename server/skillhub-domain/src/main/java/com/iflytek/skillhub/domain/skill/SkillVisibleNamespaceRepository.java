package com.iflytek.skillhub.domain.skill;

import java.util.List;

/** Persistence port for per-skill additional department access grants. */
public interface SkillVisibleNamespaceRepository {
    List<SkillVisibleNamespace> findBySkillId(Long skillId);

    SkillVisibleNamespace save(SkillVisibleNamespace grant);

    void deleteBySkillId(Long skillId);

    default List<Long> findNamespaceIdsBySkillId(Long skillId) {
        return findBySkillId(skillId).stream()
                .map(SkillVisibleNamespace::getNamespaceId)
                .toList();
    }
}
