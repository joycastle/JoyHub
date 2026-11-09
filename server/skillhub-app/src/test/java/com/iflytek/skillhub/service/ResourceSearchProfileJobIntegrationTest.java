package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.task.ResourceSearchProfileGenerationTask;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ResourceSearchProfileJobIntegrationTest.JpaConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@Import(ResourceSearchProfileJobService.class)
class ResourceSearchProfileJobIntegrationTest {
    @Autowired private ResourceSearchProfileJobService jobs;
    @Autowired private ResourceSearchDocumentJpaRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void twoWorkersCannotClaimTheSameRow() throws Exception {
        var document = repository.saveAndFlush(document());
        assertThat(jobs.candidates()).contains(document.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> jobs.claim(document.getId()));
            var second = executor.submit(() -> jobs.claim(document.getId()));
            assertThat(List.of(first.get().isPresent(), second.get().isPresent()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jobs.candidates()).doesNotContain(document.getId());
    }

    @Test
    void externalCallsHaveNoTransactionAndSuccessPreservesHumanCategory() {
        repository.saveAndFlush(document());
        var ai = mock(OpenAiResponsesClient.class);
        var embedding = mock(SearchEmbeddingService.class);
        given(ai.generateSearchProfile(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new ResourceSearchProfile(List.of(), List.of(), List.of(), List.of(), List.of(),
                            "GENERAL", ResourceCategoryCode.DATA_ANALYTICS);
                });
        given(embedding.embed(anyString())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "vector";
        });
        var properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-only-placeholder");
        new ResourceSearchProfileGenerationTask(properties, jobs, ai, new ObjectMapper(), embedding)
                .generatePendingProfiles();
        var saved = repository.findByResourceTypeAndResourceId("TOOL", 33L).orElseThrow();
        assertThat(saved.getGenerationStatus()).isEqualTo("READY");
        assertThat(saved.getCategoryCode()).isEqualTo(ResourceCategoryCode.CREATIVE_MEDIA);
        assertThat(saved.getGenerationAttempts()).isEqualTo(1);
    }

    private ResourceSearchDocumentEntity document() {
        var document = ResourceSearchDocumentEntity.basic("TOOL", 33L, null, "owner", "AI Imagine", "ai-imagine",
                "summary", "[]", "OPEN", "COMPANY", "PUBLISHED", "documentation", "hash");
        document.setAuthorCategory(ResourceCategoryCode.CREATIVE_MEDIA);
        return document;
    }
    @Configuration
    @EnableJpaRepositories(basePackageClasses = ResourceSearchDocumentJpaRepository.class,
            excludeFilters = @Filter(type = FilterType.REGEX,
                    pattern = "com\\.iflytek\\.skillhub\\.infra\\.jpa\\.(?!ResourceSearchDocumentJpaRepository$).*"))
    static class JpaConfiguration {
        @Bean
        PersistenceManagedTypes managedTypes() {
            return PersistenceManagedTypes.of(ResourceSearchDocumentEntity.class.getName());
        }
    }

}
