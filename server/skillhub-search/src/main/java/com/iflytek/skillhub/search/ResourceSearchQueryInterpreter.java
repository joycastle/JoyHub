package com.iflytek.skillhub.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Converts employee phrasing into stable terms and explicit resource/access constraints. */
@Component
public class ResourceSearchQueryInterpreter {
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
        Set<String> resourceTypes = new LinkedHashSet<>();
        if (containsAny(normalized, "agent", "agents", "assistant", "assistants", "智能体", "机器人", "助手")) {
            resourceTypes.add("AGENT");
        }
        if (containsAny(normalized, "tool", "tools", "工具")) {
            resourceTypes.add("TOOL");
        }
        if (containsAny(normalized, "skill", "skills", "技能")) {
            resourceTypes.add("SKILL");
        }

        Set<String> accessModes = new LinkedHashSet<>();
        if (containsAny(normalized, "安装", "install")) {
            accessModes.add("INSTALL");
        }
        if (containsAny(normalized, "下载", "download")) {
            accessModes.add("DOWNLOAD");
        }
        if (containsAny(normalized, "直接使用", "在线", "打开", "open", "directly use", "directly usable", "ready to use")) {
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

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
