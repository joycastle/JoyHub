package com.iflytek.skillhub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class LocalApiTokenConfigTest {

    private static final String API_TOKENS_ENABLED = "skillhub.auth.api-tokens.enabled";
    private static final String DEVICE_AUTH_VERIFICATION_URI = "skillhub.device-auth.verification-uri";

    @Test
    void localProfile_enablesApiTokensForCliDeviceFlowByDefault() throws IOException {
        ConfigurableEnvironment environment = loadLocalEnvironment(Map.of());

        assertEquals("true", environment.getProperty(API_TOKENS_ENABLED));
        assertEquals("http://localhost:3000/cli/auth", environment.getProperty(DEVICE_AUTH_VERIFICATION_URI));
    }

    @Test
    void localProfile_allowsEnvironmentVariableToDisableApiTokens() throws IOException {
        ConfigurableEnvironment environment = loadLocalEnvironment(
                Map.of(
                        "SKILLHUB_API_TOKENS_ENABLED", "false",
                        "DEVICE_AUTH_VERIFICATION_URI", "https://joyhub.example.com/cli/auth"
                )
        );

        assertEquals("false", environment.getProperty(API_TOKENS_ENABLED));
        assertEquals(
                "https://joyhub.example.com/cli/auth",
                environment.getProperty(DEVICE_AUTH_VERIFICATION_URI)
        );
    }

    private ConfigurableEnvironment loadLocalEnvironment(Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", envVars));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : List.of("application-local.yml", "application.yml")) {
            for (org.springframework.core.env.PropertySource<?> propertySource : loader.load(
                    resourceName,
                    new ClassPathResource(resourceName)
            )) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        return environment;
    }
}
