package com.joycastle.joyhub.runner.deployment;

import com.joycastle.joyhub.runner.config.RunnerProperties;
import com.joycastle.joyhub.runner.exception.RunnerException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class HttpStaticDeploymentVerifier implements StaticDeploymentVerifier {
    private final RunnerProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpStaticDeploymentVerifier(RunnerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void verifyAvailable(String slug) {
        String url = properties.verificationUrl(slug);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new RunnerException("STATIC_VERIFICATION_FAILED",
                        "Static host verification returned HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new RunnerException("STATIC_VERIFICATION_FAILED", "Static host verification failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunnerException("STATIC_VERIFICATION_FAILED", "Static host verification was interrupted", exception);
        }
    }
}
