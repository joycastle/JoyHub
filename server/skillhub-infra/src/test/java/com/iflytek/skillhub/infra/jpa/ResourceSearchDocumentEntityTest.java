package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceSearchDocumentEntityTest {

    @Test
    void aiCategoryIsAppliedWhenSourceIsAi() {
        ResourceSearchDocumentEntity document = basicDocument();

        document.applyAiCategory(ResourceCategoryCode.DATA_ANALYTICS);

        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.DATA_ANALYTICS);
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AI);
    }

    @Test
    void aiCategoryDoesNotOverwriteAuthorCategory() {
        ResourceSearchDocumentEntity document = basicDocument();
        document.setAuthorCategory(ResourceCategoryCode.GAME_DEV_QA);

        document.applyAiCategory(ResourceCategoryCode.DATA_ANALYTICS);

        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.GAME_DEV_QA);
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AUTHOR);
    }

    @Test
    void switchingBackToAiAllowsTheNextAiCategory() {
        ResourceSearchDocumentEntity document = basicDocument();
        document.setAuthorCategory(ResourceCategoryCode.GAME_DEV_QA);

        document.useAiCategory();
        document.applyAiCategory(ResourceCategoryCode.AI_ENGINEERING);

        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.AI_ENGINEERING);
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AI);
        assertThat(document.getGenerationStatus()).isEqualTo("PENDING");
    }

    @Test
    void unchangedSourceSyncPreservesGeneratedScenarios() {
        var document = basicDocument();
        document.applyGeneratedProfile("[]", "[\"generated-scenario\"]", "[]", "[]", "[]", "[]", "GENERAL",
                "profile", "model", "v1", "vector", ResourceCategoryCode.OTHER);
        document.refreshBasic(null, "owner", "title", "slug", "summary", "[]", "INSTALL", "PUBLIC", "PUBLISHED",
                "docs", document.getSourceHash(), true);
        assertThat(document.getScenariosJson()).isEqualTo("[\"generated-scenario\"]");
    }

    private ResourceSearchDocumentEntity basicDocument() {
        return ResourceSearchDocumentEntity.basic("SKILL", 1L, null, "owner", "title", "slug", "summary",
                "[]", "INSTALL", "PUBLIC", "ACTIVE", "documentation", "hash");
    }
}
