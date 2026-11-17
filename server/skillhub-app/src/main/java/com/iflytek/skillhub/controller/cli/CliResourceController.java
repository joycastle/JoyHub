package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceDiscoveryItem;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.repository.McpResourceQueryRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CLI discovery shares the MCP resource projection, visibility checks and ranking. */
@RestController
@RequestMapping("/api/cli/v1/resources")
public class CliResourceController extends BaseApiController {
    private final McpResourceQueryRepository resources;

    public CliResourceController(ApiResponseFactory responses, McpResourceQueryRepository resources) {
        super(responses);
        this.resources = resources;
    }

    @GetMapping("/search")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<PageResponse<ResourceDiscoveryItem>> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "20") int limit,
                                      @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) throw new UnauthorizedException("error.auth.required");
        return ok("response.success.read", resources.search(principal.userId(), q, "ALL", 0, Math.min(Math.max(limit, 1), 50)));
    }
}
