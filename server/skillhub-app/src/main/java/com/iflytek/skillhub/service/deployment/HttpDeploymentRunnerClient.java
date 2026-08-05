package com.iflytek.skillhub.service.deployment;

import com.iflytek.skillhub.config.DeploymentRunnerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpDeploymentRunnerClient implements DeploymentRunnerClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpDeploymentRunnerClient.class);

    private final RestClient restClient;

    public HttpDeploymentRunnerClient(DeploymentRunnerProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getRunnerBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getRunnerToken())
                .build();
    }

    @Override
    public RunnerDeploymentResult deploy(RunnerDeploymentRequest request, byte[] artifact, String filename) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("request", request).contentType(MediaType.APPLICATION_JSON);
        body.part("file", new NamedByteArrayResource(artifact, filename))
                .contentType(MediaType.parseMediaType("application/zip"));
        return exchange("/internal/v1/static/deploy", body.build());
    }

    @Override
    public RunnerDeploymentResult rollback(RunnerSwitchRequest request) {
        return postJson("/internal/v1/static/rollback", request);
    }

    @Override
    public RunnerDeploymentResult offline(RunnerOfflineRequest request) {
        return postJson("/internal/v1/static/offline", request);
    }

    @Override
    public RunnerDeploymentResult restore(RunnerSwitchRequest request) {
        return postJson("/internal/v1/static/restore", request);
    }

    private RunnerDeploymentResult exchange(String path, MultiValueMap<String, ?> body) {
        try {
            RunnerDeploymentResult result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RunnerDeploymentResult.class);
            return result != null ? result : RunnerDeploymentResult.failed(
                    "RUNNER_EMPTY_RESPONSE", "Runner returned no deployment result");
        } catch (RestClientException exception) {
            logger.warn("Runner deployment request failed [path={}]", path, exception);
            return RunnerDeploymentResult.failed("RUNNER_UNAVAILABLE", "Deployment runner is unavailable");
        }
    }

    private RunnerDeploymentResult postJson(String path, Object request) {
        try {
            RunnerDeploymentResult result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RunnerDeploymentResult.class);
            return result != null ? result : RunnerDeploymentResult.failed(
                    "RUNNER_EMPTY_RESPONSE", "Runner returned no deployment result");
        } catch (RestClientException exception) {
            logger.warn("Runner deployment request failed [path={}]", path, exception);
            return RunnerDeploymentResult.failed("RUNNER_UNAVAILABLE", "Deployment runner is unavailable");
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
