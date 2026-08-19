package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslation;
import com.iflytek.skillhub.domain.skill.SkillDocumentationTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService.ReadableSkillFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillDocumentationTranslationServiceTest {

    @Mock
    private OpenAiResponsesClient aiClient;
    @Mock
    private SkillQueryService skillQueryService;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private SkillDocumentationTranslationRepository translationRepository;

    private DiscoveryAiProperties properties;
    private SkillDocumentationTranslationService service;
    private SkillFile file;

    @BeforeEach
    void setUp() {
        properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setTranslationModel("Luna");
        properties.setModel("gpt-5.6-terra");
        service = new SkillDocumentationTranslationService(
                properties, aiClient, skillQueryService, skillFileRepository, translationRepository);
        file = new SkillFile(20L, "SKILL.md", 12L, "text/markdown", "abc123", "packages/1/20/SKILL.md");
    }

    @Test
    void returnsCachedChineseTranslationWithoutCallingTheModel() {
        when(skillQueryService.resolveReadableFile(
                "global", "weights-and-biases", "1.0.0", "SKILL.md", "user-1", Map.of()))
                .thenReturn(new ReadableSkillFile(9L, 20L, file));
        when(translationRepository.findByVersionIdAndPathAndLanguage(20L, "SKILL.md", "zh-CN"))
                .thenReturn(Optional.of(new SkillDocumentationTranslation(
                        9L, 20L, "SKILL.md", "zh-CN", "abc123", "# 已缓存", "Luna")));

        String translated = service.translate(
                "global", "weights-and-biases", "1.0.0", "SKILL.md", "zh-CN", "user-1", Map.of());

        assertThat(translated).isEqualTo("# 已缓存");
        verify(aiClient, never()).translateMarkdown(any(), any(), any());
        verify(skillQueryService, never()).getFileContentByVersionId(any(), any());
    }

    @Test
    void generatesAndStoresChineseTranslationOnCacheMiss() throws Exception {
        when(skillQueryService.resolveReadableFile(
                "global", "demo", "1.0.0", "SKILL.md", "user-1", Map.of()))
                .thenReturn(new ReadableSkillFile(9L, 20L, file));
        when(translationRepository.findByVersionIdAndPathAndLanguage(20L, "SKILL.md", "zh-CN"))
                .thenReturn(Optional.empty());
        when(skillQueryService.getFileContentByVersionId(20L, "SKILL.md"))
                .thenReturn(new java.io.ByteArrayInputStream("# Hello".getBytes(StandardCharsets.UTF_8)));
        when(aiClient.translateMarkdown(eq("# Hello"), eq("zh-CN"), any())).thenReturn("# 你好");
        when(translationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String translated = service.translate(
                "global", "demo", "1.0.0", "SKILL.md", "zh-CN", "user-1", Map.of());

        assertThat(translated).isEqualTo("# 你好");
        ArgumentCaptor<SkillDocumentationTranslation> captor =
                ArgumentCaptor.forClass(SkillDocumentationTranslation.class);
        verify(translationRepository).save(captor.capture());
        assertThat(captor.getValue().getLanguage()).isEqualTo("zh-CN");
        assertThat(captor.getValue().getSourceSha256()).isEqualTo("abc123");
        assertThat(captor.getValue().getMarkdown()).isEqualTo("# 你好");
    }

    @Test
    void warmsChineseDocumentationFromSkillMarkdown() throws Exception {
        when(skillFileRepository.findByVersionId(20L)).thenReturn(List.of(file));
        when(translationRepository.findByVersionIdAndPathAndLanguage(20L, "SKILL.md", "zh-CN"))
                .thenReturn(Optional.empty());
        when(skillQueryService.getFileContentByVersionId(20L, "SKILL.md"))
                .thenReturn(new java.io.ByteArrayInputStream("# Hello".getBytes(StandardCharsets.UTF_8)));
        when(aiClient.translateMarkdown(eq("# Hello"), eq("zh-CN"), any())).thenReturn("# 你好");
        when(translationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.warmChineseDocumentation(9L, 20L, "publisher-1");

        verify(aiClient).translateMarkdown(eq("# Hello"), eq("zh-CN"), any());
        verify(translationRepository).save(any());
    }
}
