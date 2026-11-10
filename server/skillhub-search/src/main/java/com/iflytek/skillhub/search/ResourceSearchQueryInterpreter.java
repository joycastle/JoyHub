package com.iflytek.skillhub.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Converts employee phrasing into stable terms and explicit resource/access constraints. */
@Component
public class ResourceSearchQueryInterpreter {
    private static final Pattern NEGATED_CONSTRAINT = Pattern.compile(
            "(?:不|别)(?:要|需要|想|用|是)?\\s*(?:再|只)?\\s*(?:推荐|使用)?\\s*(?:这些|那些|这种|那种)?\\s*(?:通用\\s*)?"
                    + "(agents?|skills?|tools?|智能体|机器人|助手|技能|工具|安装|下载|install|download)",
            Pattern.CASE_INSENSITIVE);
    // Task phrases, not resource names. Keep expansions narrow so an unrelated word
    // such as "generation" alone cannot make a resource eligible.
    private static final List<TaskVocabulary> TASK_VOCABULARY = List.of(
            new TaskVocabulary(List.of("画图", "画画", "绘图", "生图", "文生图", "图片生成", "生成图片", "图像生成", "image generation", "text-to-image"),
                    List.of("图片生成", "生成图片", "image generation")),
            new TaskVocabulary(List.of("做视频", "制作视频", "生成视频", "视频生成", "视频制作", "video generation", "text-to-video"),
                    List.of("视频生成", "视频制作", "video generation")),
            new TaskVocabulary(List.of("做报表", "整理报表", "生成报表", "制作报表", "做报告", "生成报告", "报告生成", "report generation"),
                    List.of("生成报告", "报表", "report generation")),
            new TaskVocabulary(List.of("做ppt", "做 ppt", "制作ppt", "制作 ppt", "幻灯片", "presentation", "slide deck"),
                    List.of("幻灯片", "演示文稿", "presentation")),
            new TaskVocabulary(List.of("画图表", "做图表", "生成图表", "图表制作", "chart generation"),
                    List.of("图表", "数据可视化", "chart generation")));
    private static final Set<String> STOP_WORDS = Set.of(
            "我", "想", "要", "做", "帮", "帮我", "请", "需要", "一个", "一份", "一下",
            "有没有", "有", "没有", "什么", "哪个", "哪些", "可以", "能够", "能", "找", "找到",
            "推荐", "使用", "直接", "的", "了", "吗", "呢",
            "i", "want", "need", "please", "help", "make", "find", "a", "an", "the", "for", "me",
            "can", "could", "use", "using", "directly");
    private static final Set<String> TYPE_TERMS = Set.of(
            "agent", "agents", "assistant", "assistants", "智能体", "机器人", "助手",
            "tool", "tools", "工具", "skill", "skills", "技能");

    private final SearchTextTokenizer tokenizer;

    public ResourceSearchQueryInterpreter(SearchTextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public ResourceSearchIntent interpret(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String constraints = NEGATED_CONSTRAINT.matcher(normalized).replaceAll(" ");
        Set<String> resourceTypes = new LinkedHashSet<>();
        if (containsAny(constraints, "agent", "agents", "assistant", "assistants", "智能体", "机器人", "助手")) {
            resourceTypes.add("AGENT");
        }
        if (containsAny(constraints, "tool", "tools", "工具")) {
            resourceTypes.add("TOOL");
        }
        if (containsAny(constraints, "skill", "skills", "技能")) {
            resourceTypes.add("SKILL");
        }
        Set<String> excludedTypes = excludedResourceTypes(normalized);
        if (!excludedTypes.isEmpty()) {
            if (resourceTypes.isEmpty()) resourceTypes.addAll(Set.of("AGENT", "TOOL", "SKILL"));
            resourceTypes.removeAll(excludedTypes);
        }

        Set<String> accessModes = new LinkedHashSet<>();
        if (containsAny(constraints, "安装", "install", "installable")) {
            accessModes.add("INSTALL");
        }
        if (containsAny(constraints, "下载", "download")) {
            accessModes.add("DOWNLOAD");
        }
        if (containsAny(constraints, "直接使用", "直接用", "在线", "打开", "open", "directly use", "directly usable", "ready to use")) {
            accessModes.add("OPEN");
        }

        List<String> terms = tokenizer.tokenizeForQuery(normalized).stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .filter(term -> term.length() > 1)
                .filter(term -> !STOP_WORDS.contains(term))
                .filter(term -> !TYPE_TERMS.contains(term))
                .distinct()
                .limit(12)
                .toList();
        return new ResourceSearchIntent(normalized, terms, Set.copyOf(resourceTypes), Set.copyOf(accessModes));
    }

    /** Small, deterministic query alternatives shared by ordinary search and the adviser. */
    public List<String> retrievalQueries(String query) {
        ResourceSearchIntent intent = interpret(query);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(intent.normalizedQuery());
        for (TaskVocabulary task : TASK_VOCABULARY) {
            boolean matches = task.phrases().stream().anyMatch(phrase -> intent.normalizedQuery().contains(phrase)
                    && TASK_VOCABULARY.stream().flatMap(other -> other.phrases().stream())
                            .noneMatch(longer -> longer.length() > phrase.length() && longer.contains(phrase)
                                    && intent.normalizedQuery().contains(longer)));
            if (matches) queries.addAll(task.queries());
        }
        return queries.stream().filter(value -> !value.isBlank()).limit(7).toList();
    }

    private record TaskVocabulary(List<String> phrases, List<String> queries) { }

    private Set<String> excludedResourceTypes(String query) {
        Set<String> excluded = new LinkedHashSet<>();
        var matcher = NEGATED_CONSTRAINT.matcher(query);
        while (matcher.find()) {
            String term = matcher.group(1);
            if (Set.of("skill", "skills", "技能").contains(term)) excluded.add("SKILL");
            if (Set.of("tool", "tools", "工具").contains(term)) excluded.add("TOOL");
            if (Set.of("agent", "agents", "智能体", "机器人", "助手").contains(term)) excluded.add("AGENT");
        }
        return Set.copyOf(excluded);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            boolean matches = term.matches("[a-z ]+")
                    ? Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(term) + "(?![a-z0-9_])").matcher(value).find()
                    : value.contains(term);
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
