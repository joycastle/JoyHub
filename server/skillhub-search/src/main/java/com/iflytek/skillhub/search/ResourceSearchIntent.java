package com.iflytek.skillhub.search;

import java.util.List;
import java.util.Set;

/** Deterministic query interpretation used before lexical and semantic recall. */
public record ResourceSearchIntent(
        String normalizedQuery,
        List<String> terms,
        Set<String> resourceTypes,
        Set<String> accessModes
) {
    public boolean hasStructuredConstraint() {
        return !resourceTypes.isEmpty() || !accessModes.isEmpty();
    }
}
