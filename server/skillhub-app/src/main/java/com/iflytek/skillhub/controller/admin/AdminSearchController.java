package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceSearchDocumentResponse;
import com.iflytek.skillhub.service.ResourceSearchProfileAdminAppService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative maintenance endpoints for search-index operations reserved for super administrators.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController extends BaseApiController {

    private final SearchRebuildService searchRebuildService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final ResourceSearchProfileAdminAppService profileAdminAppService;

    public AdminSearchController(ApiResponseFactory responseFactory,
                                 SearchRebuildService searchRebuildService,
                                 AuditLogService auditLogService,
                                 RequestIdAccessor requestIdAccessor,
                                 ResourceSearchProfileAdminAppService profileAdminAppService) {
        super(responseFactory);
        this.searchRebuildService = searchRebuildService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.profileAdminAppService = profileAdminAppService;
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> rebuildAll(@AuthenticationPrincipal PlatformPrincipal principal,
                                        HttpServletRequest httpRequest) {
        searchRebuildService.rebuildAll();
        auditLogService.record(
                principal.userId(),
                "REBUILD_SEARCH_INDEX",
                "SEARCH_INDEX",
                null,
                requestIdAccessor.current(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                "{\"scope\":\"ALL\"}"
        );
        return ok("response.success.updated", null);
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PageResponse<ResourceSearchDocumentResponse>> documents(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String generationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", profileAdminAppService.list(resourceType, generationStatus, page, size));
    }

    @GetMapping("/documents/{resourceType}/{resourceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ResourceSearchDocumentResponse> document(
            @PathVariable String resourceType,
            @PathVariable Long resourceId) {
        return ok("response.success.read", profileAdminAppService.get(resourceType, resourceId));
    }

    @PostMapping("/documents/{resourceType}/{resourceId}/regenerate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ResourceSearchDocumentResponse> regenerate(
            @PathVariable String resourceType,
            @PathVariable Long resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        ResourceSearchDocumentResponse response = profileAdminAppService.requestRegeneration(resourceType, resourceId);
        auditLogService.record(principal.userId(), "REGENERATE_RESOURCE_SEARCH_PROFILE", "RESOURCE_SEARCH_DOCUMENT",
                resourceId, requestIdAccessor.current(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"),
                "{\"resourceType\":\"" + resourceType + "\"}");
        return ok("response.success.updated", response);
    }
}
