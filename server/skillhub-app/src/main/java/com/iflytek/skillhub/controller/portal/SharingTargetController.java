package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SharingTargetResponse;
import com.iflytek.skillhub.service.SharingTargetQueryAppService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Common authenticated department list used by skill and catalog sharing forms. */
@RestController
@RequestMapping({"/api/v1", "/api/web"})
public class SharingTargetController extends BaseApiController {
    private final SharingTargetQueryAppService sharingTargetQueryAppService;

    public SharingTargetController(SharingTargetQueryAppService sharingTargetQueryAppService,
                                   ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.sharingTargetQueryAppService = sharingTargetQueryAppService;
    }

    @GetMapping("/sharing-targets")
    public ApiResponse<List<SharingTargetResponse>> listSharingTargets() {
        return ok("response.success.read", sharingTargetQueryAppService.list());
    }
}
