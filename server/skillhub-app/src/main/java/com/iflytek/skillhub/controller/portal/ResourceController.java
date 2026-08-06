package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.ResourceAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified resource workspace endpoint for Skills and static Catalog resources. */
@RestController
@RequestMapping({"/api/web/resources", "/api/v1/resources"})
@Tag(name = "Resources")
public class ResourceController extends BaseApiController {
    private final ResourceAppService resourceAppService;

    public ResourceController(ResourceAppService resourceAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.resourceAppService = resourceAppService;
    }

    @GetMapping("/mine")
    @Operation(summary = "List all resources maintained by the current user")
    public ApiResponse<PageResponse<ResourceSummaryResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        return ok("response.success.read", resourceAppService.listMine(
                principal.userId(), page, size, kind, q));
    }
}
