package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceSearchProfileJobServiceTest {
    private ResourceSearchDocumentJpaRepository repository;
    private ResourceSearchDocumentEntity document;
    private ResourceSearchProfileJobService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResourceSearchDocumentJpaRepository.class);
        document = ResourceSearchDocumentEntity.basic("TOOL", 33L, null, "owner", "AI Imagine", "ai-imagine",
                "summary", "[]", "OPEN", "COMPANY", "PUBLISHED", "documentation", "original-hash");
        given(repository.findForGeneration(1L)).willReturn(Optional.of(document));
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        service = new ResourceSearchProfileJobService(repository);
    }

    @Test
    void retriesFailureAfterCooldownAndPreservesHumanCategory() {
        document.setAuthorCategory(ResourceCategoryCode.CREATIVE_MEDIA);
        var job = service.claim(1L).orElseThrow();
        assertThat(service.claim(1L)).isEmpty();
        service.fail(job, new AiRelayException("AI_HTTP_503", true));
        assertThat(document.getGenerationStatus()).isEqualTo("FAILED");
        assertThat(document.getGenerationErrorCode()).isEqualTo("AI_HTTP_503");
        assertThat(document.getGenerationAttempts()).isEqualTo(1);
        assertThat(document.getGenerationNextRetryAt()).isAfter(Instant.now().plusSeconds(55));
        assertThat(service.claim(1L)).isEmpty();
        assertThat(document.canClaimGeneration(document.getGenerationNextRetryAt().plusSeconds(1))).isTrue();
        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.CREATIVE_MEDIA);
    }

    @Test
    void stopsAfterFourAttemptsAndOnPermanentFailures() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            // Advance a retry deadline without sleeping or issuing external requests.
            if (attempt > 1) document.failGeneration("AI_TIMEOUT", Instant.EPOCH);
            var job = service.claim(1L).orElseThrow();
            service.fail(job, new AiRelayException("AI_TIMEOUT", true));
        }
        assertThat(document.getGenerationAttempts()).isEqualTo(4);
        assertThat(document.getGenerationNextRetryAt()).isNull();
        assertThat(service.claim(1L)).isEmpty();
        document.requestRegeneration();
        var manual = service.claim(1L).orElseThrow();
        service.fail(manual, new AiRelayException("AI_HTTP_401", false));
        assertThat(document.getGenerationNextRetryAt()).isNull();
        assertThat(document.getGenerationAttempts()).isEqualTo(1);
    }

    @Test
    void ignoresStaleSuccessAndFailureAfterDocumentChangesOrManualRegeneration() {
        var old = service.claim(1L).orElseThrow();
        document.refreshBasic(null, "owner", "new title", "ai-imagine", "summary", "[]", "OPEN", "COMPANY",
                "PUBLISHED", "changed documentation", "new-hash", true);
        service.complete(old, profile());
        service.fail(old, new AiRelayException("AI_HTTP_503", true));
        assertThat(document.getGenerationStatus()).isEqualTo("PENDING");
        assertThat(document.getGenerationAttempts()).isZero();
        var current = service.claim(1L).orElseThrow();
        document.requestRegeneration();
        service.complete(current, profile());
        assertThat(document.getGenerationStatus()).isEqualTo("PENDING");
    }

    @Test
    void recoversExpiredLeaseAndRejectsItsLateResult() {
        document.claimGeneration("expired", Instant.EPOCH);
        var job = service.claim(1L).orElseThrow();
        service.complete(new ResourceSearchProfileJobService.Job(1L, "expired", "original-hash", "TOOL", 33L,
                "old title", "summary", "documentation"), profile());
        assertThat(document.getGenerationStatus()).isEqualTo("PENDING");
        service.complete(job, profile());
        assertThat(document.getGenerationStatus()).isEqualTo("READY");
        assertThat(document.getGenerationLeaseToken()).isNull();
        assertThat(document.getGenerationNextRetryAt()).isNull();
        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.CREATIVE_MEDIA);
    }

    @Test
    void unchangedIndexSyncDoesNotResetRetryBudgetOrDelay() {
        var job = service.claim(1L).orElseThrow();
        service.fail(job, new AiRelayException("AI_TIMEOUT", true));
        Instant retryAt = document.getGenerationNextRetryAt();
        document.refreshBasic(null, "owner", "AI Imagine", "ai-imagine", "summary", "[]", "OPEN", "COMPANY",
                "PUBLISHED", "documentation", "original-hash", true);
        assertThat(document.getGenerationAttempts()).isEqualTo(1);
        assertThat(document.getGenerationNextRetryAt()).isEqualTo(retryAt);
    }

    @Test
    void backoffIncreasesAndHonorsRelayRetryAfter() {
        assertThat(ResourceSearchProfileJobService.retryDelay(1, Duration.ZERO).toSeconds()).isBetween(60L, 72L);
        assertThat(ResourceSearchProfileJobService.retryDelay(2, Duration.ZERO).toSeconds()).isBetween(300L, 360L);
        assertThat(ResourceSearchProfileJobService.retryDelay(3, Duration.ZERO).toSeconds()).isBetween(1500L, 1800L);
        assertThat(ResourceSearchProfileJobService.retryDelay(1, Duration.ofHours(1))).isEqualTo(Duration.ofHours(1));
    }

    private ResourceSearchProfileJobService.GeneratedProfile profile() {
        return new ResourceSearchProfileJobService.GeneratedProfile("[]", "[]", "[]", "[]", "[]", "GENERAL",
                "profile", "gpt-6.1-terra", "test", "vector", ResourceCategoryCode.CREATIVE_MEDIA);
    }
}
