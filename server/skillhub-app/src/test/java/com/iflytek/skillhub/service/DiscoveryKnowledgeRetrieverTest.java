package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscoveryKnowledgeRetrieverTest {
    @Test
    void selectsTheMostRelevantDocumentationParagraph() {
        String document = """
                # 安装
                下载插件并重新启动编辑器。

                # 数据分析
                支持 SQL 查询、经营报表、趋势分析和数据可视化。

                # 反馈
                请联系维护人提交问题。
                """;

        assertThat(DiscoveryKnowledgeRetriever.bestExcerpt("分析经营数据并生成报表", document, "fallback"))
                .contains("SQL 查询", "经营报表", "数据可视化");
    }
}
