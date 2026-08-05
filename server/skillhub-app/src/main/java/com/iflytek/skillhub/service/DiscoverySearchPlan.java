package com.iflytek.skillhub.service;

import java.util.List;

/** A generic, model-produced decomposition of an employee goal into retrieval steps. */
public record DiscoverySearchPlan(String goal, List<Step> steps) {
    public DiscoverySearchPlan {
        goal = goal == null ? "" : goal.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static DiscoverySearchPlan singleStep(String question) {
        String normalized = question == null ? "" : question.trim();
        return new DiscoverySearchPlan(normalized, List.of(new Step(normalized, List.of(normalized))));
    }

    public record Step(String objective, List<String> queries) {
        public Step {
            objective = objective == null ? "" : objective.trim();
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }
}
