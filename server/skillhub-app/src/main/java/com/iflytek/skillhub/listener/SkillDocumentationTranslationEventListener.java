package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.service.SkillDocumentationTranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists one Chinese documentation translation after publish so readers do not wait on the model. */
@Component
public class SkillDocumentationTranslationEventListener {
    private static final Logger log = LoggerFactory.getLogger(SkillDocumentationTranslationEventListener.class);
    private static final int BACKFILL_BATCH = 2;

    private final SkillDocumentationTranslationService translationService;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillDocumentationTranslationRepository translationRepository;
    private final DiscoveryAiProperties properties;

    public SkillDocumentationTranslationEventListener(SkillDocumentationTranslationService translationService,
                                                      SkillVersionRepository skillVersionRepository,
                                                      SkillDocumentationTranslationRepository translationRepository,
                                                      DiscoveryAiProperties properties) {
        this.translationService = translationService;
        this.skillVersionRepository = skillVersionRepository;
        this.translationRepository = translationRepository;
        this.properties = properties;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onSkillPublished(SkillPublishedEvent event) {
        translationService.warmChineseDocumentation(event.skillId(), event.versionId(), event.publisherId());
    }

    @Scheduled(fixedDelayString = "${joyhub.ai.translation-backfill-delay-ms:120000}")
    public void backfillExistingDocumentation() {
        if (properties == null || !properties.isEnabled()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return;
        }
        for (Long versionId : translationRepository.findPublishedVersionIdsMissingLanguage(
                SkillDocumentationTranslationService.CHINESE, BACKFILL_BATCH)) {
            skillVersionRepository.findById(versionId).ifPresent(this::warmQuietly);
        }
    }

    private void warmQuietly(SkillVersion version) {
        try {
            translationService.warmChineseDocumentation(version.getSkillId(), version.getId(), version.getCreatedBy());
        } catch (RuntimeException exception) {
            log.warn("Could not backfill Chinese documentation [versionId={}]: {}",
                    version.getId(), exception.getMessage());
        }
    }
}
