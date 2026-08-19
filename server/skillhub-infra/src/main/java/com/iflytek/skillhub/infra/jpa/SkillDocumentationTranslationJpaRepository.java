package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslation;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillDocumentationTranslationJpaRepository
        extends JpaRepository<SkillDocumentationTranslation, Long>, SkillDocumentationTranslationRepository {
    Optional<SkillDocumentationTranslation> findByVersionIdAndPathAndLanguage(
            Long versionId, String path, String language);

    @Override
    @Query(value = """
            SELECT sv.id
            FROM skill_version sv
            WHERE sv.status = 'PUBLISHED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM skill_documentation_translation t
                  WHERE t.version_id = sv.id
                    AND t.language = :language
              )
            ORDER BY sv.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findPublishedVersionIdsMissingLanguage(@Param("language") String language, @Param("limit") int limit);
}
