package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.CatalogDepartmentResponse;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.repository.MySkillQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceAppServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private CatalogResourceRepository catalogResourceRepository;

    @Mock
    private MySkillQueryRepository mySkillQueryRepository;

    @Mock
    private CatalogResourceProjectionAssembler catalogProjectionAssembler;

    @Mock
    private Skill skill;

    @Mock
    private CatalogResource catalogResource;

    private ResourceAppService service;

    @BeforeEach
    void setUp() {
        service = new ResourceAppService(
                skillRepository,
                catalogResourceRepository,
                mySkillQueryRepository,
                catalogProjectionAssembler);
    }

    @Test
    void listMineCombinesSkillAndCatalogSourcesIntoOneSortedPage() {
        Instant skillUpdatedAt = Instant.parse("2026-08-06T10:00:00Z");
        Instant catalogUpdatedAt = Instant.parse("2026-08-06T11:00:00Z");
        SkillSummaryResponse skillSummary = new SkillSummaryResponse(
                7L, "review-skill", "Review Skill", "Review text", "PUBLIC", "ACTIVE",
                12L, 3, null, 1, "team-ai", skillUpdatedAt, false,
                new SkillLifecycleVersionResponse(70L, "1.2.0", "PUBLISHED"), null, null, "PUBLISHED");
        CatalogResourceSummaryResponse catalogSummary = new CatalogResourceSummaryResponse(
                8L, "static-tool", "Static Tool", "Tool text", "ONLINE_TOOL", null, null, "1.0.0",
                new CatalogDepartmentResponse(2L, "team-ai", "Team AI"), null, "PUBLISHED", "ACTIVE",
                "WAREHOUSE", Set.of(), Set.of(), true, catalogUpdatedAt);

        given(skillRepository.findByOwnerId("owner")).willReturn(List.of(skill));
        given(mySkillQueryRepository.getSkillSummaries(List.of(skill), "owner"))
                .willReturn(List.of(skillSummary));
        given(catalogResourceRepository.findByOwnerId("owner")).willReturn(List.of(catalogResource));
        given(catalogProjectionAssembler.summaries(List.of(catalogResource))).willReturn(List.of(catalogSummary));

        var result = service.listMine("owner", 0, 10, null, null);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting("resourceId")
                .containsExactly("catalog:8", "skill:7");
        assertThat(result.items()).extracting("sourceType")
                .containsExactly("CATALOG", "SKILL");
    }

    @Test
    void listMineAppliesKindAndKeywordBeforeBuildingThePage() {
        given(catalogResourceRepository.findByOwnerId("owner")).willReturn(List.of(catalogResource));
        given(catalogResource.getKind()).willReturn(CatalogResourceKind.ONLINE_TOOL);
        given(catalogResource.getName()).willReturn("Static Tool");
        given(catalogResource.getSlug()).willReturn("static-tool");
        given(catalogResource.getSummary()).willReturn("Tool text");

        var result = service.listMine("owner", 0, 10, "online_tool", "missing");

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.items()).isEmpty();
    }
}
