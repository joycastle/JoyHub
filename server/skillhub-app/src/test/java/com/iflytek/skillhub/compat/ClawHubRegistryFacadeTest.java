package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubRegistrySearchResponse;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.UnifiedResourceSearchAppService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClawHubRegistryFacadeTest {

    @Test
    void search_mapsInstantToEpochMillis() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        UnifiedResourceSearchAppService unifiedResourceSearchAppService = mock(UnifiedResourceSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                unifiedResourceSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Instant updatedAt = Instant.parse("2026-03-18T09:00:00Z");
        SkillSummaryResponse skill = new SkillSummaryResponse(
                                1L,
                                "time-skill",
                                "Time Skill",
                                "summary",
                                "PUBLIC",
                                "ACTIVE",
                                12L,
                                3,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                updatedAt,
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null,
                                "PUBLISHED"
                        );
        when(unifiedResourceSearchAppService.search("agent", null, null, "relevance",
                UnifiedResourceSearchType.SKILL, false, 0, 20, null, Map.of(), null))
                .thenReturn(new PageResponse<>(List.of(new UnifiedResourceSearchItemResponse(
                        "SKILL", "INSTALL", 0.9D, skill, null)), 1, 0, 20));

        ClawHubRegistrySearchResponse result = facade.search("agent", 20, null, Map.of());

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).updatedAt())
                .isEqualTo(updatedAt.toEpochMilli());
    }
}
