package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.ResourceAppService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {

    @Mock
    private ResourceAppService resourceAppService;

    private ResourceController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", Locale.getDefault(), "ok");
        controller = new ResourceController(
                resourceAppService,
                new ApiResponseFactory(
                        messageSource,
                        Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC),
                        new RequestIdAccessor()));
    }

    @Test
    void listMineDelegatesTheUnifiedPagingAndFilterContract() {
        ResourceSummaryResponse resource = new ResourceSummaryResponse(
                "skill:7", "SKILL", 7L, "SKILL", "demo-skill", "Demo Skill", "summary",
                "team-ai", "ACTIVE", "1.0.0", "PUBLISHED", "PUBLIC", 1L, 2, 3, true,
                Instant.parse("2026-08-06T00:00:00Z"));
        PageResponse<ResourceSummaryResponse> page = new PageResponse<>(List.of(resource), 1, 2, 10);
        PlatformPrincipal principal = new PlatformPrincipal("owner", "Owner", "owner@example.com", null, "local", Set.of("USER"));
        given(resourceAppService.listMine("owner", 2, 10, "SKILL", "demo"))
                .willReturn(page);

        var response = controller.listMine(2, 10, "SKILL", "demo", principal);

        assertThat(response.data()).isEqualTo(page);
    }

    @Test
    void listMineRequiresAnAuthenticatedPrincipal() {
        assertThatThrownBy(() -> controller.listMine(0, 24, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
