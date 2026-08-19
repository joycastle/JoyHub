package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillDocumentationTranslationRepository {
    Optional<SkillDocumentationTranslation> findByVersionIdAndPathAndLanguage(
            Long versionId, String path, String language);

    SkillDocumentationTranslation save(SkillDocumentationTranslation translation);

    List<Long> findPublishedVersionIdsMissingLanguage(String language, int limit);
}
