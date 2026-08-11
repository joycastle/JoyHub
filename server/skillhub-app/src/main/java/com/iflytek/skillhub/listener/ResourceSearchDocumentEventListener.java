package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.service.ResourceSearchDocumentSyncService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Updates the shared resource search projection after committed Skill lifecycle changes. */
@Component
public class ResourceSearchDocumentEventListener {
    private final SkillRepository skillRepository;
    private final ResourceSearchDocumentSyncService syncService;

    public ResourceSearchDocumentEventListener(SkillRepository skillRepository,
                                               ResourceSearchDocumentSyncService syncService) {
        this.skillRepository = skillRepository;
        this.syncService = syncService;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSkillPublished(SkillPublishedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(syncService::synchronizeSkill);
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSkillStatusChanged(SkillStatusChangedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(syncService::synchronizeSkill);
    }
}
