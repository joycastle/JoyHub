package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVisibleNamespace;
import com.iflytek.skillhub.domain.skill.SkillVisibleNamespaceRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data adapter for per-skill department access grants. */
@Repository
public interface SkillVisibleNamespaceJpaRepository
        extends JpaRepository<SkillVisibleNamespace, Long>, SkillVisibleNamespaceRepository {
    List<SkillVisibleNamespace> findBySkillIdOrderByNamespaceId(Long skillId);

    @Override
    default List<SkillVisibleNamespace> findBySkillId(Long skillId) {
        return findBySkillIdOrderByNamespaceId(skillId);
    }

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SkillVisibleNamespace svn where svn.skillId = :skillId")
    void deleteBySkillId(@Param("skillId") Long skillId);
}
