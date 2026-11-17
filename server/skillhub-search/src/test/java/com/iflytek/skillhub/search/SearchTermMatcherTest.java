package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SearchTermMatcherTest {
    @Test
    void englishWordsRequireBoundaries() {
        assertThat(SearchTermMatcher.contains("gameplay clips and clients", "cli")).isFalse();
        assertThat(SearchTermMatcher.contains("an ai-imagine-cli package", "cli")).isTrue();
        assertThat(SearchTermMatcher.contains("email contains details", "ai")).isFalse();
        assertThat(SearchTermMatcher.contains("批量生成图片", "生成图片")).isTrue();
    }

    @Test
    void approximateNamesUseWordDistanceRatherThanSubstrings() {
        assertThat(SearchTermMatcher.similarIdentityWord("nova imagine cli", "image")).isTrue();
        assertThat(SearchTermMatcher.similarIdentityWord("canvas studio", "canavs")).isTrue();
        assertThat(SearchTermMatcher.similarIdentityWord("gameplay clips", "cli")).isFalse();
        assertThat(SearchTermMatcher.similarIdentityWord("unrelated editor", "image")).isFalse();
    }
}
