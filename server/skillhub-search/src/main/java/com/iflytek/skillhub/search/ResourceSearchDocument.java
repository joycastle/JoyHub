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
        double qualityScore,
        java.util.Set<String> accessModes
) {
    public ResourceSearchDocument(String id, String resourceType, String title, String slug, String summary,
                                  List<String> scenarios, List<String> tags, String documentation,
                                  String accessMode, String semanticVector, double qualityScore) {
        this(id, resourceType, title, slug, summary, scenarios, tags, documentation, accessMode,
                semanticVector, qualityScore, java.util.Set.of(accessMode));
    }

    public ResourceSearchDocument {
        accessModes = accessModes == null ? java.util.Set.of(accessMode) : java.util.Set.copyOf(accessModes);
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
