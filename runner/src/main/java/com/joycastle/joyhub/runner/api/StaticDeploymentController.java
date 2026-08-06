package com.joycastle.joyhub.runner.api;

import com.joycastle.joyhub.runner.deployment.StaticDeploymentService;
import com.joycastle.joyhub.runner.exception.RunnerException;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/v1/static")
public class StaticDeploymentController {
    private final StaticDeploymentService deploymentService;

    public StaticDeploymentController(StaticDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunnerDeploymentResult deploy(
            @RequestPart("request") RunnerDeploymentRequest request,
            @RequestPart("file") MultipartFile file) {
        try {
            return deploymentService.deploy(request, file.getBytes());
        } catch (RunnerException exception) {
            return RunnerDeploymentResult.failed(exception);
        } catch (IOException exception) {
            return RunnerDeploymentResult.failed(new RunnerException(
                    "ARTIFACT_READ_FAILED", "Unable to read the static ZIP", exception));
        }
    }

    @PostMapping("/rollback")
    public RunnerDeploymentResult rollback(@RequestBody RunnerSwitchRequest request) {
        try {
            return deploymentService.rollback(request);
        } catch (RunnerException exception) {
            return RunnerDeploymentResult.failed(exception);
        }
    }

    @PostMapping("/offline")
    public RunnerDeploymentResult offline(@RequestBody RunnerOfflineRequest request) {
        try {
            return deploymentService.offline(request);
        } catch (RunnerException exception) {
            return RunnerDeploymentResult.failed(exception);
        }
    }

    @PostMapping("/restore")
    public RunnerDeploymentResult restore(@RequestBody RunnerSwitchRequest request) {
        try {
            return deploymentService.restore(request);
        } catch (RunnerException exception) {
            return RunnerDeploymentResult.failed(exception);
        }
    }

    @GetMapping("/{slug}/state")
    public RunnerStateResponse state(@PathVariable String slug) {
        return deploymentService.state(slug);
    }
}
