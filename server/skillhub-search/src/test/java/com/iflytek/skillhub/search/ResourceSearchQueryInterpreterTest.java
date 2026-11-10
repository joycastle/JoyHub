package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceSearchQueryInterpreterTest {
    private final ResourceSearchQueryInterpreter interpreter = new ResourceSearchQueryInterpreter(new SearchTextTokenizer());

    @Test
    void rejectingSkillsKeepsToolsAndAgentsInsteadOfSelectingSkills() {
        var intent = interpreter.interpret("我想就是纯画图功能，不是这些skill");
        assertThat(intent.resourceTypes()).containsExactlyInAnyOrder("TOOL", "AGENT");
    }

    @Test
    void productNamesDoNotAccidentallyBecomeResourceOrAccessFilters() {
        var intent = interpreter.interpret("OpenAI SkillHub integration");
        assertThat(intent.accessModes()).isEmpty();
        assertThat(intent.resourceTypes()).isEmpty();
    }

    @Test
    void noInstallRequirementDoesNotSelectInstallOnlyResources() {
        var intent = interpreter.interpret("不需要安装，能直接用的画图工具");
        assertThat(intent.resourceTypes()).containsExactly("TOOL");
        assertThat(intent.accessModes()).containsExactly("OPEN");
    }

    @Test
    void negativeComparisonDoesNotOverridePositiveSkillPreference() {
        var intent = interpreter.interpret("优先推荐 Skill，不要只推荐通用 Agent");
        assertThat(intent.resourceTypes()).containsExactly("SKILL");
    }
}
