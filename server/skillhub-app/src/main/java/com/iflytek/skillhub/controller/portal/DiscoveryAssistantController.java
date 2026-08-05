package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.DiscoveryAssistRequest;
import com.iflytek.skillhub.dto.DiscoveryAssistResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.DiscoveryAssistantAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/web/discovery", "/api/v1/discovery"})
@Tag(name = "JoyHub Discovery")
public class DiscoveryAssistantController extends BaseApiController {
    private final DiscoveryAssistantAppService assistantAppService;

    public DiscoveryAssistantController(DiscoveryAssistantAppService assistantAppService,
                                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.assistantAppService = assistantAppService;
    }

    @PostMapping("/assist")
    @RateLimit(category = "discovery-ai", authenticated = 20, anonymous = 0)
    @Operation(summary = "Ask the permission-aware JoyHub discovery assistant")
    public ApiResponse<DiscoveryAssistResponse> assist(
            @Valid @RequestBody DiscoveryAssistRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        if (principal == null) {
            throw new DomainForbiddenException("error.auth.required");
        }
        return ok("response.success.read", assistantAppService.assist(
                request.question().trim(), request.language(), request.conversationId(),
                principal, namespaceRoles));
    }
}
