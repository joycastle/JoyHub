package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.service.DiscoveryAiClient;
import com.iflytek.skillhub.service.LabelSearchSyncService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Generates persisted Chinese card metadata after a Skill version is published. */
@Component
public class SkillLocalizationEventListener {
    private static final Logger log = LoggerFactory.getLogger(SkillLocalizationEventListener.class);

    private final SkillRepository skillRepository;
    private final DiscoveryAiClient aiClient;
    private final DiscoveryAiProperties properties;
    private final LabelSearchSyncService searchSyncService;

    public SkillLocalizationEventListener(SkillRepository skillRepository, DiscoveryAiClient aiClient,
                                          DiscoveryAiProperties properties, LabelSearchSyncService searchSyncService) {
        this.skillRepository = skillRepository;
        this.aiClient = aiClient;
        this.properties = properties;
        this.searchSyncService = searchSyncService;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onSkillPublished(SkillPublishedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> localize(skill, event.publisherId()));
    }

    /** Gradually backfills older Skills without delaying searches or application startup. */
    @Scheduled(fixedDelayString = "${joyhub.ai.localization-backfill-delay-ms:60000}")
    public void backfillExistingSkills() {
        if (properties == null || !properties.isEnabled()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return;
        }
        skillRepository.findAll().stream()
                .filter(skill -> skill.getDisplayName() != null && !skill.getDisplayName().isBlank())
                .filter(skill -> skill.getSummary() != null && !skill.getSummary().isBlank())
                .filter(skill -> !sourceHash(skill.getDisplayName(), skill.getSummary())
                        .equals(skill.getLocalizationSourceHash()))
                .limit(4)
                .forEach(skill -> localize(skill, skill.getOwnerId()));
    }

    private void localize(Skill skill, String publisherId) {
        String name = skill.getDisplayName();
        String summary = skill.getSummary();
        if (name == null || name.isBlank() || summary == null || summary.isBlank()) {
            return;
        }
        String sourceHash = sourceHash(name, summary);
        if (sourceHash.equals(skill.getLocalizationSourceHash())
                && skill.getLocalizedDisplayName() != null && skill.getLocalizedSummary() != null) {
            return;
        }
        try {
            DiscoveryAiClient.LocalizedSkillMetadata localized = aiClient.localizeSkillMetadata(
                    name, summary, "zh-CN", "skill-localization:" + publisherId);
            skill.setLocalizedDisplayName(localized.displayName());
            skill.setLocalizedSummary(localized.summary());
            skill.setLocalizationSourceHash(sourceHash);
            skill.setUpdatedBy(publisherId);
            skillRepository.save(skill);
            searchSyncService.rebuildSkill(skill.getId());
        } catch (RuntimeException exception) {
            log.warn("Could not localize published Skill [skillId={}]; source text remains available", skill.getId());
        }
    }

    private String sourceHash(String name, String summary) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((name + "\n" + summary).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
