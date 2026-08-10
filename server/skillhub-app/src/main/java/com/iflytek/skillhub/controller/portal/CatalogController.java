package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AgentDocumentationDraftRequest;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.ArchiveDocumentationDraftResponse;
import com.iflytek.skillhub.dto.CatalogPublishRequest;
import com.iflytek.skillhub.dto.CatalogResourceRequest;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.CatalogTransferRequest;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.CatalogArtifactAppService;
import com.iflytek.skillhub.service.AgentDocumentationAiService;
import com.iflytek.skillhub.service.ArchiveDocumentationAiService;
import com.iflytek.skillhub.service.CatalogDeploymentLifecycleAppService;
import com.iflytek.skillhub.service.CatalogResourceCommandAppService;
import com.iflytek.skillhub.service.CatalogResourceQueryAppService;
import com.iflytek.skillhub.service.CatalogDocumentExtractionService;
import com.iflytek.skillhub.service.CatalogViewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/web/catalog", "/api/v1/catalog"})
@Tag(name = "JoyHub Catalog")
public class CatalogController extends BaseApiController {
    private final CatalogResourceQueryAppService queryAppService;
    private final CatalogResourceCommandAppService commandAppService;
    private final CatalogDeploymentLifecycleAppService deploymentLifecycleAppService;
    private final CatalogArtifactAppService artifactAppService;
    private final CatalogDocumentExtractionService documentExtractionService;
    private final AgentDocumentationAiService agentDocumentationAiService;
    private final ArchiveDocumentationAiService archiveDocumentationAiService;

    public CatalogController(CatalogResourceQueryAppService queryAppService,
                             CatalogResourceCommandAppService commandAppService,
                             CatalogDeploymentLifecycleAppService deploymentLifecycleAppService,
                             CatalogArtifactAppService artifactAppService, CatalogDocumentExtractionService documentExtractionService,
                             AgentDocumentationAiService agentDocumentationAiService,
                             ArchiveDocumentationAiService archiveDocumentationAiService,
                             ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.queryAppService = queryAppService;
        this.commandAppService = commandAppService;
        this.deploymentLifecycleAppService = deploymentLifecycleAppService;
        this.artifactAppService = artifactAppService;
        this.documentExtractionService = documentExtractionService;
        this.agentDocumentationAiService = agentDocumentationAiService;
        this.archiveDocumentationAiService = archiveDocumentationAiService;
    }

    @PostMapping(value = "/document-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> extractDocument(@RequestPart("file") MultipartFile file) {
        return ok("response.success.read", documentExtractionService.extract(file));
    }

    @PostMapping("/agent-documentation-draft")
    @Operation(summary = "Generate a reviewable Agent usage-guide draft")
    public ApiResponse<String> generateAgentDocumentationDraft(
            @Valid @RequestBody AgentDocumentationDraftRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        if (principal == null) {
            throw CatalogDomainException.forbidden("error.auth.required");
        }
        return ok("response.success.read", agentDocumentationAiService.draft(request, principal.userId(), language));
    }

    @PostMapping(value = "/tool-documentation-draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Generate a reviewable Tool usage-guide draft from an uploaded ZIP")
    public ApiResponse<ArchiveDocumentationDraftResponse> generateToolDocumentationDraft(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        if (principal == null) {
            throw CatalogDomainException.forbidden("error.auth.required");
        }
        return ok("response.success.read", archiveDocumentationAiService.draft(
                file, principal.userId(), language));
    }

    @GetMapping("/resources")
    @Operation(summary = "Search visible published Catalog resources")
    public ApiResponse<PageResponse<CatalogResourceSummaryResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) CatalogResourceKind kind,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        CatalogViewer viewer = viewer(principal, namespaceRoles);
        return ok("response.success.read", queryAppService.search(
                q, center, kind, scenario, departmentId, sort, viewer,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @GetMapping("/resources/{slug}")
    @Operation(summary = "Get a viewer-specific Catalog resource detail")
    public ApiResponse<CatalogResourceDetailResponse> detail(
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", queryAppService.detail(slug, viewer(principal, namespaceRoles)));
    }

    @GetMapping("/me/resources")
    @Operation(summary = "List Catalog resources maintained by the current user")
    public ApiResponse<PageResponse<CatalogResourceSummaryResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", queryAppService.mine(
                viewer(principal, namespaceRoles),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @PostMapping("/resources")
    @Operation(summary = "Create a Catalog resource or resume the current user's draft with the same slug")
    public ApiResponse<CatalogResourceDetailResponse> create(
            @Valid @RequestBody CatalogResourceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.created", commandAppService.create(request, viewer(principal, namespaceRoles)));
    }

    @PutMapping("/resources/{slug}")
    @Operation(summary = "Update a maintained Catalog resource")
    public ApiResponse<CatalogResourceDetailResponse> update(
            @PathVariable String slug,
            @Valid @RequestBody CatalogResourceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.updated", commandAppService.update(
                slug, request, viewer(principal, namespaceRoles)));
    }

    @PostMapping("/resources/{slug}/publish")
    @Operation(summary = "Publish or republish a Catalog resource")
    public ApiResponse<CatalogResourceDetailResponse> publish(
            @PathVariable String slug,
            @Valid @RequestBody(required = false) CatalogPublishRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", deploymentLifecycleAppService.publish(
                slug,
                request != null ? request.version() : null,
                viewer(principal, namespaceRoles),
                AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/resources/{slug}/offline")
    @Operation(summary = "Take a Catalog resource offline")
    public ApiResponse<CatalogResourceDetailResponse> offline(
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", deploymentLifecycleAppService.takeOffline(
                slug, viewer(principal, namespaceRoles), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/resources/{slug}/transfer")
    @Operation(summary = "Transfer Catalog resource maintenance ownership")
    public ApiResponse<CatalogResourceDetailResponse> transfer(
            @PathVariable String slug,
            @Valid @RequestBody CatalogTransferRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.updated", commandAppService.transfer(
                slug, request.newOwnerId(), viewer(principal, namespaceRoles)));
    }

    @PostMapping(value = "/resources/{slug}/artifact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a Catalog ZIP artifact")
    public ApiResponse<CatalogResourceDetailResponse> uploadArtifact(
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.updated", artifactAppService.upload(
                slug, file, viewer(principal, namespaceRoles)));
    }

    @GetMapping("/resources/{slug}/artifact")
    @Operation(summary = "Download a visible Catalog artifact")
    public ResponseEntity<InputStreamResource> downloadArtifact(
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        CatalogArtifactAppService.CatalogArtifactDownload download = artifactAppService.download(
                slug, viewer(principal, namespaceRoles));
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
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(new InputStreamResource(download.stream()));
    }

    private CatalogViewer viewer(PlatformPrincipal principal, Map<Long, NamespaceRole> namespaceRoles) {
        if (principal == null) {
            throw CatalogDomainException.forbidden("error.auth.required");
        }
        return new CatalogViewer(
                principal.userId(),
                namespaceRoles != null ? namespaceRoles : Map.of(),
                principal.platformRoles() != null ? principal.platformRoles() : Set.of()
        );
    }
}
