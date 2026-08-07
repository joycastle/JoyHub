package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResourceReferenceTest {
    @Test
    void parsesStableSourceReference() {
        ResourceReference reference = ResourceReference.parse("catalog:42");

        assertThat(reference.sourceType()).isEqualTo("CATALOG");
        assertThat(reference.sourceId()).isEqualTo(42L);
        assertThat(reference.value()).isEqualTo("catalog:42");
    }

    @Test
    void rejectsMalformedReference() {
        assertThatThrownBy(() -> ResourceReference.parse("skill:not-a-number"))
                .hasMessageContaining("error.resource.reference.invalid");
    }
}
