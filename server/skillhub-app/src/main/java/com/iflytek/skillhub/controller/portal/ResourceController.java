package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceActionResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.dto.ResourceStatsResponse;
import com.iflytek.skillhub.dto.RecommendedResourceResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.CatalogViewer;
import com.iflytek.skillhub.service.ResourceAppService;
import com.iflytek.skillhub.service.ResourceDownloadAppService;
import com.iflytek.skillhub.service.ResourceFavoriteAppService;
import com.iflytek.skillhub.service.ResourceLifecycleAppService;
import com.iflytek.skillhub.service.ResourceStatsAppService;
import com.iflytek.skillhub.service.ResourceRecommendationAppService;
import com.iflytek.skillhub.service.UnifiedResourceSearchAppService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified resource workspace endpoint for Skills and static Catalog resources. */
@RestController
@RequestMapping({"/api/web/resources", "/api/v1/resources"})
@Tag(name = "Resources")
public class ResourceController extends BaseApiController {
    private final ResourceAppService resourceAppService;
    private final ResourceLifecycleAppService resourceLifecycleAppService;
    private final ResourceFavoriteAppService resourceFavoriteAppService;
    private final ResourceDownloadAppService resourceDownloadAppService;
    private final ResourceStatsAppService resourceStatsAppService;
    private final UnifiedResourceSearchAppService unifiedResourceSearchAppService;
    private final ResourceRecommendationAppService recommendationAppService;

    public ResourceController(ResourceAppService resourceAppService,
                              ResourceLifecycleAppService resourceLifecycleAppService,
                              ResourceFavoriteAppService resourceFavoriteAppService,
                              ResourceDownloadAppService resourceDownloadAppService,
                              ResourceStatsAppService resourceStatsAppService,
                              UnifiedResourceSearchAppService unifiedResourceSearchAppService,
                              ResourceRecommendationAppService recommendationAppService,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.resourceAppService = resourceAppService;
        this.resourceLifecycleAppService = resourceLifecycleAppService;
        this.resourceFavoriteAppService = resourceFavoriteAppService;
        this.resourceDownloadAppService = resourceDownloadAppService;
        this.resourceStatsAppService = resourceStatsAppService;
        this.unifiedResourceSearchAppService = unifiedResourceSearchAppService;
        this.recommendationAppService = recommendationAppService;
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Recommend visible Agents, Tools, and Skills for the current department")
    public ApiResponse<java.util.List<RecommendedResourceResponse>> recommendations(
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        Map<Long, NamespaceRole> roles = namespaceRoles != null ? namespaceRoles : Map.of();
        CatalogViewer viewer = principal == null ? null : new CatalogViewer(principal.userId(), roles,
                principal.platformRoles() != null ? principal.platformRoles() : Set.of());
        return ok("response.success.read", recommendationAppService.recommend(
                size, principal != null ? principal.userId() : null, roles, viewer));
    }

    @GetMapping("/search")
    @Operation(summary = "Search Skills, Agents, and Tools in one ranked resource pool")
    public ApiResponse<PageResponse<UnifiedResourceSearchItemResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String label,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "ALL") UnifiedResourceSearchType type,
            @RequestParam(defaultValue = "false") boolean starredOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        Map<Long, NamespaceRole> roles = namespaceRoles != null ? namespaceRoles : Map.of();
        String userId = principal != null ? principal.userId() : null;
        CatalogViewer catalogViewer = principal == null ? null : new CatalogViewer(
                principal.userId(), roles,
                principal.platformRoles() != null ? principal.platformRoles() : Set.of());
        return ok("response.success.read", unifiedResourceSearchAppService.search(
                q, namespace, label, sort, type, starredOnly, page, size, userId, roles, catalogViewer));
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

    @PostMapping("/{resourceId}/archive")
    @Operation(summary = "Archive an owned resource")
    public ApiResponse<ResourceActionResponse> archive(
            @PathVariable String resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest request) {
        return ok("response.success.updated", resourceLifecycleAppService.archive(
                resourceId, requireUser(principal), namespaceRoles, platformRoles(principal),
                AuditRequestContext.from(request)));
    }

    @PostMapping("/{resourceId}/unarchive")
    @Operation(summary = "Restore an owned resource from archive")
    public ApiResponse<ResourceActionResponse> unarchive(
            @PathVariable String resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest request) {
        return ok("response.success.updated", resourceLifecycleAppService.unarchive(
                resourceId, requireUser(principal), namespaceRoles, platformRoles(principal),
                AuditRequestContext.from(request)));
    }

    @PostMapping("/{resourceId}/publish")
    @Operation(summary = "Publish a resource through the common lifecycle")
    public ApiResponse<ResourceActionResponse> publish(
            @PathVariable String resourceId,
            @RequestParam(required = false) String version,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest request) {
        return ok("response.success.updated", resourceLifecycleAppService.publish(
                resourceId, version, requireUser(principal), namespaceRoles, platformRoles(principal),
                AuditRequestContext.from(request)));
    }

    @PostMapping("/{resourceId}/offline")
    @Operation(summary = "Take a resource offline through the common lifecycle")
    public ApiResponse<ResourceActionResponse> offline(
            @PathVariable String resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest request) {
        return ok("response.success.updated", resourceLifecycleAppService.offline(
                resourceId, requireUser(principal), namespaceRoles, platformRoles(principal),
                AuditRequestContext.from(request)));
    }

    @PutMapping("/{resourceId}/favorite")
    @Operation(summary = "Favorite a resource")
    public ApiResponse<Boolean> favorite(@PathVariable String resourceId,
                                         @AuthenticationPrincipal PlatformPrincipal principal) {
        String userId = requireUser(principal);
        resourceFavoriteAppService.favorite(resourceId, userId);
        return ok("response.success.updated", true);
    }

    @DeleteMapping("/{resourceId}/favorite")
    @Operation(summary = "Remove a resource favorite")
    public ApiResponse<Boolean> unfavorite(@PathVariable String resourceId,
                                           @AuthenticationPrincipal PlatformPrincipal principal) {
        String userId = requireUser(principal);
        resourceFavoriteAppService.unfavorite(resourceId, userId);
        return ok("response.success.updated", false);
    }

    @GetMapping("/{resourceId}/favorite")
    @Operation(summary = "Read the current user's resource favorite state")
    public ApiResponse<Boolean> favoriteState(@PathVariable String resourceId,
                                               @AuthenticationPrincipal PlatformPrincipal principal) {
        String userId = requireUser(principal);
        return ok("response.success.read", resourceFavoriteAppService.isFavorited(resourceId, userId));
    }

    @GetMapping("/{resourceId}/stats")
    @Operation(summary = "Read unified resource usage statistics")
    public ApiResponse<ResourceStatsResponse> stats(
            @PathVariable String resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", resourceStatsAppService.get(
                resourceId, principal != null ? principal.userId() : null));
    }

    @PostMapping("/{resourceId}/stats/view")
    @Operation(summary = "Record a resource detail view")
    public ApiResponse<Void> recordView(@PathVariable String resourceId) {
        resourceStatsAppService.recordView(resourceId);
        return ok("response.success.updated", null);
    }

    @PostMapping("/{resourceId}/stats/use")
    @Operation(summary = "Record a resource use action")
    public ApiResponse<Void> recordUse(@PathVariable String resourceId) {
        resourceStatsAppService.recordUse(resourceId);
        return ok("response.success.updated", null);
    }

    @GetMapping("/{resourceId}/download")
    @Operation(summary = "Download the current published resource artifact")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String resourceId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        ResourceDownloadAppService.ResourceDownload download = resourceDownloadAppService.download(
                resourceId,
                principal != null ? principal.userId() : null,
                namespaceRoles,
                platformRoles(principal));
        MediaType contentType;
        try {
            contentType = download.contentType() != null
                    ? MediaType.parseMediaType(download.contentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.stream()));
    }

    private String requireUser(PlatformPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new UnauthorizedException("error.auth.required");
        }
        return principal.userId();
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal != null && principal.platformRoles() != null ? principal.platformRoles() : Set.of();
    }
}
