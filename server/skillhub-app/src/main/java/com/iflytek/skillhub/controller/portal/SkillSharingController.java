package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillSharingRequest;
import com.iflytek.skillhub.dto.SkillSharingResponse;
import com.iflytek.skillhub.service.SkillSharingAppService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner/admin-only management API for explicit department access grants. */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillSharingController extends BaseApiController {
    private final SkillSharingAppService skillSharingAppService;

    public SkillSharingController(SkillSharingAppService skillSharingAppService,
                                  ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillSharingAppService = skillSharingAppService;
    }

    @GetMapping({"/{namespace}/{slug}/sharing", "/{namespace}/{slug}/sharing-targets"})
    public ApiResponse<SkillSharingResponse> list(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read", skillSharingAppService.list(
                namespace, slug, userId, userNsRoles == null ? Map.of() : userNsRoles,
                platformRoles == null ? Set.of() : platformRoles));
    }

    @PutMapping({"/{namespace}/{slug}/sharing", "/{namespace}/{slug}/sharing-targets"})
    public ApiResponse<SkillSharingResponse> replace(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody SkillSharingRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.updated", skillSharingAppService.replace(
                namespace, slug, request.namespaceIds(), userId,
                userNsRoles == null ? Map.of() : userNsRoles,
                platformRoles == null ? Set.of() : platformRoles,
                request.restricted()));
    }
}
