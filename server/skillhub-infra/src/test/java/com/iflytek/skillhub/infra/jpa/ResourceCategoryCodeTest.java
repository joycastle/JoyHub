package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceCategoryCodeTest {

    @Test
    void acceptsEveryFixedCategoryCodeCaseInsensitively() {
        for (ResourceCategoryCode expected : ResourceCategoryCode.values()) {
            assertThat(ResourceCategoryCode.fromExternal(expected.name().toLowerCase()))
                    .isEqualTo(expected);
        }
    }

    @Test
    void fallsBackToOtherForMissingOrUnknownValues() {
        assertThat(ResourceCategoryCode.fromExternal(null)).isEqualTo(ResourceCategoryCode.OTHER);
        assertThat(ResourceCategoryCode.fromExternal(" ")).isEqualTo(ResourceCategoryCode.OTHER);
        assertThat(ResourceCategoryCode.fromExternal("not-a-category")).isEqualTo(ResourceCategoryCode.OTHER);
    }
}
