package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HybridResourceSearchRankerTest {
    private final SearchEmbeddingService embeddings = new HashingSearchEmbeddingService();
    private final HybridResourceSearchRanker ranker = new HybridResourceSearchRanker(
            embeddings,
            new ResourceSearchQueryInterpreter(new SearchTextTokenizer()));

    @Test
    void agentWordsPreferAgentsWithoutImplicitlyFilteringOtherTypes() {
        var resources = List.of(
                document("1", "AGENT", "通用王总", "就在飞书里处理工作", "OPEN"),
                document("2", "TOOL", "九宫切图", "UI 图片九宫格切图工具", "OPEN"),
                document("3", "SKILL", "每日站会日志", "生成每日站会和复盘", "INSTALL")
        );

        assertThat(ranker.rank("帮我找一个能直接使用的 Agent", resources, 10, true))
                .extracting(result -> result.document().title())
                .startsWith("通用王总").hasSize(3);
    }

    @Test
    void lexicalAndSemanticRecallFindsImageSlicingTool() {
        var resources = List.of(
                document("1", "TOOL", "九宫切图", "UI 图片九宫格切图工具", "OPEN"),
                document("2", "TOOL", "曲线编辑器", "编辑游戏地图路线", "OPEN")
        );

        assertThat(ranker.rank("切图", resources, 10, false))
                .extracting(result -> result.document().title())
                .containsExactly("九宫切图");
    }

    @Test
    void installWordsPreferInstallableResourcesWithoutRemovingNamedTools() {
        var resources = List.of(
                document("1", "AGENT", "通用王总", "生成日报", "OPEN"),
                document("2", "SKILL", "每日站会日志", "生成每日站会、日报和复盘", "INSTALL")
        );

        assertThat(ranker.rank("安装一个写日报的技能", resources, 10, true))
                .extracting(result -> result.document().title())
                .startsWith("每日站会日志").hasSize(2);
    }

    @Test
    void chineseSemanticRecallFindsDailyStandupForDailyReportQuery() {
        SearchEmbeddingService bge = new BgeSearchEmbeddingService();
        HybridResourceSearchRanker semanticRanker = new HybridResourceSearchRanker(
                bge,
                new ResourceSearchQueryInterpreter(new SearchTextTokenizer()));
        String dailyText = "daily-standup-journal Generate concise daily standups, reflection prompts, "
                + "and weekly retrospectives for individuals or teams. Use for planning a day, "
                + "surfacing blockers, reviewing user-provided entries, or drafting a check-in.";
        String dailyVector = bge.embed(dailyText);
        double dailySimilarity = bge.similarity("日报", dailyVector);
        assertThat(dailySimilarity).isGreaterThan(0.50D);
        String unrelatedText = "安全威胁建模 security-threat-model 识别代码仓库中的信任边界、攻击路径和缓解措施。";
        String unrelatedVector = bge.embed(unrelatedText);
        double unrelatedSimilarity = bge.similarity("日报", unrelatedVector);
        assertThat(unrelatedSimilarity).isLessThan(0.40D);
        var resources = List.of(
                new ResourceSearchDocument(
                        "1", "SKILL", "每日站会日志", "daily-standup-journal",
                        "Generate concise daily standups, reflection prompts, and weekly retrospectives.",
                        List.of(), List.of(), "", "INSTALL", dailyVector, 0D),
                new ResourceSearchDocument(
                        "2", "SKILL", "安全威胁建模", "security-threat-model",
                        "识别代码仓库中的信任边界、攻击路径和缓解措施。",
                        List.of(), List.of(), "", "INSTALL", unrelatedVector, 0D)
        );

        assertThat(semanticRanker.rankWithinScope("日报", resources, 10, false))
                .extracting(result -> result.document().title())
                .startsWith("每日站会日志");
    }

    @Test
    void reportQueryRejectsUnrelatedToolsWhenStrongReportMatchesExist() {
        SearchEmbeddingService bge = new BgeSearchEmbeddingService();
        HybridResourceSearchRanker semanticRanker = new HybridResourceSearchRanker(
                bge,
                new ResourceSearchQueryInterpreter(new SearchTextTokenizer()));
        var resources = List.of(
                semanticDocument(bge, "1", "AGENT", "通用王总",
                        "在飞书中处理文件、生成图片与多格式报告。", "OPEN"),
                semanticDocument(bge, "2", "SKILL", "每周HTML报告生成",
                        "把飞书表格、Excel或CSV数据生成响应式HTML报告。", "INSTALL"),
                semanticDocument(bge, "3", "TOOL", "构建包",
                        "Bingo Frenzy 游戏构建包下载入口。", "DOWNLOAD"),
                semanticDocument(bge, "4", "TOOL", "图集拆分",
                        "图集拆分和美术资源提取工具。", "OPEN"),
                semanticDocument(bge, "5", "TOOL", "棋盘预览",
                        "二合玩法棋盘预览与布局调试工具。", "OPEN")
        );

        assertThat(semanticRanker.rank("生成报告", resources, 10, false))
                .extracting(result -> result.document().title())
                .containsExactly("每周HTML报告生成", "通用王总");
    }

    @Test
    void addingOtherHitsOrChangingScopeCannotRemoveARelevantResource() {
        SearchEmbeddingService fixed = new SearchEmbeddingService() {
            public String embed(String text) { return text; }
            public double similarity(String text, String vector) { return Double.parseDouble(vector); }
        };
        var stable = new HybridResourceSearchRanker(fixed, new ResourceSearchQueryInterpreter(new SearchTextTokenizer()));
        var tool = new ResourceSearchDocument("1", "TOOL", "AI Imagine CLI", "ai-imagine", "生成图片",
                List.of(), List.of(), "", "OPEN", "0.566", 0D);
        var skill = new ResourceSearchDocument("2", "SKILL", "AI Image prompts", "image-prompts", "Write prompts",
                List.of(), List.of(), "", "INSTALL", "0.70", 0D);
        assertThat(stable.rank("AI image", List.of(tool, skill), 10, false))
                .extracting(result -> result.document().id()).contains("1");
        assertThat(stable.rankWithinScope("AI image", List.of(tool), 10, false))
                .extracting(result -> result.document().id()).containsExactly("1");
    }

    @Test
    void productIdentityBeatsIncidentalMentionsAndRequestWordsDoNotExcludeIt() {
        var tool = document("1", "TOOL", "Nova Imagine CLI", "生成图片与视频", "OPEN");
        var clip = new ResourceSearchDocument("2", "SKILL", "Video prompts", "video-prompts", "AI image prompts",
                List.of("gameplay clips"), List.of(), "", "INSTALL", embeddings.embed("AI image prompts gameplay clips"), 0D);
        for (String query : List.of("nova image cli", "安装 Nova Imagine", "下载 Nova Imagine", "Nova Imagine skill")) {
            assertThat(ranker.rank(query, List.of(clip, tool), 10, false))
                    .extracting(result -> result.document().id()).startsWith("1");
        }
    }

    @Test
    void approximateProductNameOutranksPopularDocumentationMentions() {
        SearchEmbeddingService fixed = new SearchEmbeddingService() {
            public String embed(String text) { return "0.65"; }
            public double similarity(String text, String vector) { return Double.parseDouble(vector); }
        };
        var stable = new HybridResourceSearchRanker(fixed, new ResourceSearchQueryInterpreter(new SearchTextTokenizer()));
        var product = new ResourceSearchDocument("1", "TOOL", "Nova Canvas CLI", "nova-canvas", "Create illustrations",
                List.of(), List.of(), "", "OPEN", "0.60", 0D);
        var guide = new ResourceSearchDocument("2", "SKILL", "Prompt guide", "prompt-guide", "Write prompts",
                List.of("Nova canva"), List.of("Nova canva"), "Nova canva prompts", "INSTALL", "0.70", 1D);
        assertThat(stable.rank("Nova canva", List.of(guide, product), 10, false))
                .extracting(result -> result.document().id()).startsWith("1");
    }

    @Test
    void directCapabilityPhraseOutranksScatteredTerms() {
        var direct = document("1", "TOOL", "Canvas studio", "生成图片和视频", "OPEN");
        var indirect = new ResourceSearchDocument("2", "SKILL", "写作指南", "writing-guide", "撰写创意提示词",
                List.of("图片", "生成"), List.of("图片", "生成"), "", "INSTALL", embeddings.embed("图片生成提示"), 0D);
        assertThat(ranker.rank("生成图片", List.of(indirect, direct), 10, false))
                .extracting(result -> result.document().id()).startsWith("1");
    }

    private ResourceSearchDocument document(String id, String type, String title, String summary, String accessMode) {
        String text = String.join(" ", title, summary, type, accessMode);
        return new ResourceSearchDocument(
                id, type, title, title, summary, List.of(), List.of(), "", accessMode,
                embeddings.embed(text), 0D);
    }

    private ResourceSearchDocument semanticDocument(SearchEmbeddingService service,
                                                    String id,
                                                    String type,
                                                    String title,
                                                    String summary,
                                                    String accessMode) {
        String text = String.join(" ", title, summary, type, accessMode);
        return new ResourceSearchDocument(
                id, type, title, title, summary, List.of(), List.of(), "", accessMode,
                service.embed(text), 0D);
    }
}
