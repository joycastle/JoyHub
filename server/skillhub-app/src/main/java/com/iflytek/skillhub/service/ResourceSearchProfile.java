package com.iflytek.skillhub.service;

import java.util.List;

/** AI-produced fields for a resource search document, before evidence validation. */
public record ResourceSearchProfile(
        List<Capability> capabilities,
        List<String> scenarios,
        List<String> inputs,
        List<String> outputs,
        List<String> searchTerms,
        String companyRelevance
) {
    public record Capability(String value, String evidence, double confidence) { }
}
