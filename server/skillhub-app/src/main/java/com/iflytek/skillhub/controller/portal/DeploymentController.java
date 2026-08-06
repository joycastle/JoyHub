package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.CreateDeployableApplicationRequest;
import com.iflytek.skillhub.dto.CreateDeploymentReleaseRequest;
import com.iflytek.skillhub.dto.DeployableApplicationResponse;
import com.iflytek.skillhub.dto.DeploymentJobResponse;
import com.iflytek.skillhub.dto.DeploymentTargetRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.CatalogViewer;
import com.iflytek.skillhub.service.DeploymentCommandAppService;
import com.iflytek.skillhub.service.DeploymentQueryAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "JoyHub Deployments")
public class DeploymentController extends BaseApiController {
    private final DeploymentCommandAppService commandAppService;
    private final DeploymentQueryAppService queryAppService;

    public DeploymentController(DeploymentCommandAppService commandAppService,
                                DeploymentQueryAppService queryAppService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.commandAppService = commandAppService;
        this.queryAppService = queryAppService;
    }

    @PostMapping("/deployable-applications")
    @Operation(summary = "Enable static deployment for an existing Catalog resource")
    public ApiResponse<DeployableApplicationResponse> create(
            @Valid @RequestBody CreateDeployableApplicationRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.created", commandAppService.create(
                request.catalogResourceId(), request.deploymentMode(), viewer(principal, namespaceRoles)));
    }

    @GetMapping("/deployable-applications/{id}")
    @Operation(summary = "Get a deployable application with releases and operations")
    public ApiResponse<DeployableApplicationResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", queryAppService.detail(id, viewer(principal, namespaceRoles)));
    }

    @PostMapping("/deployable-applications/{id}/releases")
    @Operation(summary = "Deploy the current immutable Catalog ZIP as a new release")
    public ApiResponse<DeployableApplicationResponse> deploy(
            @PathVariable Long id,
            @Valid @RequestBody CreateDeploymentReleaseRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", commandAppService.deploy(
                id, request.version(), viewer(principal, namespaceRoles), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/deployment-jobs/{jobId}")
    @Operation(summary = "Get a deployment operation result")
    public ApiResponse<DeploymentJobResponse> job(
            @PathVariable Long jobId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", queryAppService.job(jobId, viewer(principal, namespaceRoles)));
    }

    @PostMapping("/deployable-applications/{id}/rollback")
    @Operation(summary = "Atomically roll back to a retained release")
    public ApiResponse<DeployableApplicationResponse> rollback(
            @PathVariable Long id,
            @Valid @RequestBody DeploymentTargetRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", commandAppService.rollback(
                id, request.targetReleaseId(), viewer(principal, namespaceRoles), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/deployable-applications/{id}/offline")
    @Operation(summary = "Take a deployed application offline without deleting releases")
    public ApiResponse<DeployableApplicationResponse> offline(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", commandAppService.offline(
                id, viewer(principal, namespaceRoles), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/deployable-applications/{id}/restore")
    @Operation(summary = "Restore a retained release at the stable application URL")
    public ApiResponse<DeployableApplicationResponse> restore(
            @PathVariable Long id,
            @Valid @RequestBody DeploymentTargetRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", commandAppService.restore(
                id, request.targetReleaseId(), viewer(principal, namespaceRoles), AuditRequestContext.from(httpRequest)));
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
