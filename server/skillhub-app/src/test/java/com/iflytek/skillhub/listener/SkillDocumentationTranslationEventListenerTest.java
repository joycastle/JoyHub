package com.iflytek.skillhub.listener;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.service.SkillDocumentationTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillDocumentationTranslationEventListenerTest {

    @Mock
    private SkillDocumentationTranslationService translationService;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillDocumentationTranslationRepository translationRepository;
    @Mock
    private DiscoveryAiProperties properties;

    private SkillDocumentationTranslationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SkillDocumentationTranslationEventListener(
                translationService, skillVersionRepository, translationRepository, properties);
    }

    @Test
    void warmsChineseDocumentationWhenASkillIsPublished() {
        when(properties.isDocumentationTranslationWarmupEnabled()).thenReturn(true);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn("test-key");

        listener.onSkillPublished(new SkillPublishedEvent(9L, 20L, "publisher-1"));

        verify(translationService).warmChineseDocumentation(9L, 20L, "publisher-1");
    }

    @Test
    void skipsPublishWarmupWhenDisabled() {
        listener.onSkillPublished(new SkillPublishedEvent(9L, 20L, "publisher-1"));

        verifyNoInteractions(translationService);
    }

    @Test
    void skipsBackfillWhenWarmupIsDisabled() {
        listener.backfillExistingDocumentation();

        verifyNoInteractions(translationRepository, skillVersionRepository, translationService);
    }
}
