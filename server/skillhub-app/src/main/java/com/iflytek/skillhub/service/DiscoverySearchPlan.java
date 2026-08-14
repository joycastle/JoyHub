package com.iflytek.skillhub.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A generic, model-produced decomposition of an employee goal into retrieval steps. */
public record DiscoverySearchPlan(String goal, List<Step> steps) {
    private static final Pattern REFINEMENT_PATTERN = Pattern.compile(
            "(?:这个|那个|上述|上面|刚才|方案|结果|推荐|只保留|改成|优先|不要|别再|再精简|继续)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_SUFFIX = Pattern.compile(
            "[，,;；]?\\s*(?:请)?(?:帮我)?(?:推荐|给出|告诉我|列出).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STEP_SEPARATOR = Pattern.compile(
            "\\s*(?:[;；]|[，,]\\s*(?:然后|并且|并|再|同时|以及)\\s*|"
                    + "(?:然后|并且|再|同时|并|以及)(?=生成|制作|导出|整理|分析|转换|创建|汇总|做成|输出|发布)|"
                    + "\\s+(?:then|and then)\\s+)\\s*",
            Pattern.CASE_INSENSITIVE);
    public DiscoverySearchPlan {
        goal = goal == null ? "" : goal.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static DiscoverySearchPlan singleStep(String question) {
        String normalized = question == null ? "" : question.trim();
        return new DiscoverySearchPlan(normalized, List.of(new Step(normalized, List.of(normalized))));
    }

    /**
     * Builds a fast deterministic retrieval plan without adding a second model round trip.
     * Compound goals are split before retrieval so one broad Agent cannot crowd out the
     * purpose-built resources for each outcome. Short refinement turns reuse the previous
     * user goal while the latest turn still supplies type/access constraints to retrieval.
     */
    public static DiscoverySearchPlan localPlan(String question, List<DiscoveryConversationTurn> history) {
        String latest = normalize(question);
        String goal = isRefinement(latest) ? previousGoal(history, latest) : latest;
        String plannable = REQUEST_SUFFIX.matcher(goal).replaceFirst("").trim();
        if (plannable.isBlank()) plannable = goal;

        List<Step> steps = STEP_SEPARATOR.splitAsStream(plannable)
                .map(DiscoverySearchPlan::normalizeObjective)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .map(objective -> new Step(objective, List.of(objective)))
                .toList();
        return steps.isEmpty() ? singleStep(goal) : new DiscoverySearchPlan(goal, steps);
    }

    private static boolean isRefinement(String question) {
        if (question.isBlank()) return false;
        String normalized = question.toLowerCase(Locale.ROOT);
        return REFINEMENT_PATTERN.matcher(normalized).find()
                && !normalized.matches(".*(?:我想|我需要|需要把|我要).{8,}.*");
    }

    public static boolean isRefinementTurn(String question) {
        return isRefinement(normalize(question));
    }

    private static String previousGoal(List<DiscoveryConversationTurn> history, String fallback) {
        if (history == null) return fallback;
        for (int index = history.size() - 1; index >= 0; index--) {
            String previous = normalize(history.get(index).question());
            if (!previous.isBlank()) return previous;
        }
        return fallback;
    }

    private static String normalizeObjective(String value) {
        return normalize(value).replaceFirst("^(?:然后|并且|并|再|同时|以及)\\s*", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceFirst("[。！？!?]+$", "");
    }

    public record Step(String objective, List<String> queries) {
        public Step {
            objective = objective == null ? "" : objective.trim();
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }
}
