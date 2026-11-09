package com.iflytek.skillhub.service;

import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short, row-locked job transitions. External AI requests run outside these transactions. */
@Service
public class ResourceSearchProfileJobService {
    static final int MAX_ATTEMPTS = 4;
    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(ResourceSearchProfileJobService.class);
    private final ResourceSearchDocumentJpaRepository repository;

    public ResourceSearchProfileJobService(ResourceSearchDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Long> candidates() {
        return repository.findGenerationCandidates(Instant.now(), PageRequest.of(0, 20));
    }

    @Transactional
    public Optional<Job> claim(Long id) {
        Instant now = Instant.now();
        ResourceSearchDocumentEntity document = repository.findForGeneration(id).orElse(null);
        if (document == null || !document.canClaimGeneration(now)) return Optional.empty();
        if (document.getGenerationAttempts() >= MAX_ATTEMPTS) {
            document.failGeneration("PROFILE_ATTEMPTS_EXHAUSTED", null);
            repository.save(document);
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        document.claimGeneration(token, now.plus(LEASE));
        repository.save(document);
        return Optional.of(new Job(id, token, document.getSourceHash(), document.getResourceType(),
                document.getResourceId(), document.getTitle(), document.getSummary(), document.getRawDocumentation()));
    }

    @Transactional
    public void complete(Job job, GeneratedProfile profile) {
        repository.findForGeneration(job.id()).filter(document -> document.ownsGeneration(job.token(), job.sourceHash()))
                .ifPresent(document -> {
                    document.applyGeneratedProfile(profile.capabilities(), profile.scenarios(), profile.inputs(),
                            profile.outputs(), profile.searchTerms(), profile.capabilities(), profile.relevance(),
                            profile.text(), profile.model(), profile.promptVersion(), profile.vector(), profile.category());
                    repository.save(document);
                });
    }

    @Transactional
    public void fail(Job job, RuntimeException exception) {
        AiRelayException failure = AiRelayException.classify(exception);
        repository.findForGeneration(job.id()).filter(document -> document.ownsGeneration(job.token(), job.sourceHash()))
                .ifPresent(document -> {
                    Instant nextRetry = failure.retryable() && document.getGenerationAttempts() < MAX_ATTEMPTS
                            ? Instant.now().plus(retryDelay(document.getGenerationAttempts(), failure.retryAfter())) : null;
                    document.failGeneration(failure.code(), nextRetry);
                    repository.save(document);
                    log.warn("Search profile generation failed [type={}, resourceId={}, attempt={}, code={}, retryAt={}]",
                            job.type(), job.resourceId(), document.getGenerationAttempts(), failure.code(), nextRetry);
                });
    }

    static Duration retryDelay(int attempt, Duration retryAfter) {
        long baseSeconds = Math.min(1800L, 60L * (long) Math.pow(5, Math.min(Math.max(attempt - 1, 0), 3)));
        Duration backoff = Duration.ofSeconds(baseSeconds + ThreadLocalRandom.current().nextLong(baseSeconds / 5 + 1));
        return retryAfter.compareTo(backoff) > 0 ? retryAfter : backoff;
    }

    public record Job(Long id, String token, String sourceHash, String type, Long resourceId,
                      String title, String summary, String documentation) { }

    public record GeneratedProfile(String capabilities, String scenarios, String inputs, String outputs,
                                   String searchTerms, String relevance, String text, String model,
                                   String promptVersion, String vector, ResourceCategoryCode category) { }
}
