package com.iflytek.skillhub.search;

import java.util.List;

/**
 * Canonical lightweight search projection shared by Skills, Agents, and Tools.
 *
 * <p>Business aggregates remain separate. Search callers project only the fields needed for
 * permission-aware recall and ranking into this record.
 */
public record ResourceSearchDocument(
        String id,
        String resourceType,
        String title,
        String slug,
        String summary,
        List<String> scenarios,
        List<String> tags,
        String documentation,
        String accessMode,
        String semanticVector,
        double qualityScore
) {
    public ResourceSearchDocument {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
