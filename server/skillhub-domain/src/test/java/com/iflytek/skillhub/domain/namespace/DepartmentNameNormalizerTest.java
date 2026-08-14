package com.iflytek.skillhub.domain.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DepartmentNameNormalizerTest {
    @Test
    void normalizesFeishuNumberAndEnglishSuffix() {
        assertThat(DepartmentNameNormalizer.normalize("部门8 Lab")).isEqualTo("lab");
        assertThat(DepartmentNameNormalizer.normalize("部门3 麦趣工作室| Matchtree Studio"))
                .isEqualTo("麦趣工作室");
    }
}
