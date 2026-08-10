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
    void explicitAgentIntentKeepsOnlyDirectlyUsableAgents() {
        var resources = List.of(
                document("1", "AGENT", "通用王总", "就在飞书里处理工作", "OPEN"),
                document("2", "TOOL", "九宫切图", "UI 图片九宫格切图工具", "OPEN"),
                document("3", "SKILL", "每日站会日志", "生成每日站会和复盘", "INSTALL")
        );

        assertThat(ranker.rank("帮我找一个能直接使用的 Agent", resources, 10, true))
                .extracting(result -> result.document().title())
                .containsExactly("通用王总");
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
    void installIntentNeverReturnsNonInstallableResources() {
        var resources = List.of(
                document("1", "AGENT", "通用王总", "生成日报", "OPEN"),
                document("2", "SKILL", "每日站会日志", "生成每日站会、日报和复盘", "INSTALL")
        );

        assertThat(ranker.rank("安装一个写日报的技能", resources, 10, true))
                .extracting(result -> result.document().title())
                .containsExactly("每日站会日志");
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

    private ResourceSearchDocument document(String id, String type, String title, String summary, String accessMode) {
        String text = String.join(" ", title, summary, type, accessMode);
        return new ResourceSearchDocument(
                id, type, title, title, summary, List.of(), List.of(), "", accessMode,
                embeddings.embed(text), 0D);
    }
}
