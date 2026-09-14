package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishTargetResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.PublishTargetQueryAppService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CLI contract for namespaces where the current identity may publish. */
@RestController
@RequestMapping("/api/cli/v1/namespaces")
public class CliNamespaceController extends BaseApiController {

    private final PublishTargetQueryAppService publishTargetQueryAppService;

    public CliNamespaceController(
            ApiResponseFactory responseFactory,
            PublishTargetQueryAppService publishTargetQueryAppService) {
        super(responseFactory);
        this.publishTargetQueryAppService = publishTargetQueryAppService;
    }

    @GetMapping("/publish-targets")
    public ApiResponse<List<PublishTargetResponse>> publishTargets(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false)
            Map<Long, NamespaceRole> namespaceRoles) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new UnauthorizedException("error.auth.required");
        }
        return ok("response.success.read", publishTargetQueryAppService.list(
                namespaceRoles,
                principal.platformRoles() != null ? principal.platformRoles() : Set.of()));
    }
}
