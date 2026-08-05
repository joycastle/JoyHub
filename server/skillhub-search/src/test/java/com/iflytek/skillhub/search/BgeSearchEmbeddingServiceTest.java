package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BgeSearchEmbeddingServiceTest {
    private final BgeSearchEmbeddingService service = new BgeSearchEmbeddingService();

    @Test
    void ranksChineseSemanticIntentWithoutExactKeywordOverlap() {
        String analytics = service.embed("商业智能报表、SQL 查询、指标可视化和经营数据洞察");
        String imageEditing = service.embed("批量裁剪图片、九宫格切图和美术素材处理");

        assertThat(service.similarity("有什么工具能分析公司的运营数据？", analytics))
                .isGreaterThan(service.similarity("有什么工具能分析公司的运营数据？", imageEditing));
    }
}
