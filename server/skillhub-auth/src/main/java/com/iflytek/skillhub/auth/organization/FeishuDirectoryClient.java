package com.iflytek.skillhub.auth.organization;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads the current user's department assignments from the Feishu contact directory. */
@Service
public class FeishuDirectoryClient {

    public static final String ATTR_SYNC_COMPLETE = "joyhub_feishu_department_sync_complete";
    public static final String ATTR_DEPARTMENTS = "joyhub_feishu_departments";

    private static final Logger log = LoggerFactory.getLogger(FeishuDirectoryClient.class);
    private static final String TOKEN_URI = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String CONTACT_BASE_URI = "https://open.feishu.cn/open-apis/contact/v3";

    private final RestClient restClient;
    private final boolean enabled;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;
    private volatile CachedToken cachedToken;

    @Autowired
    public FeishuDirectoryClient(
            @Value("${joyhub.feishu.department-sync.enabled:true}") boolean enabled,
            @Value("${spring.security.oauth2.client.registration.feishu.client-id:}") String clientId,
            @Value("${spring.security.oauth2.client.registration.feishu.client-secret:}") String clientSecret) {
        this(RestClient.create(), enabled, Clock.systemUTC(), clientId, clientSecret);
    }

    FeishuDirectoryClient(
            RestClient restClient,
            boolean enabled,
            Clock clock,
            String clientId,
            String clientSecret) {
        this.restClient = restClient;
        this.enabled = enabled;
        this.clock = clock;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Returns empty when synchronization is disabled or Feishu cannot be queried. An optional
     * containing an empty list means the lookup succeeded and the user has no departments.
     */
    public Optional<List<FeishuDepartment>> loadDepartments(String openId) {
        if (!enabled || openId == null || openId.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return Optional.empty();
        }
        try {
            String token = tenantAccessToken();
            Map<String, Object> userResponse = restClient.get()
                    .uri(CONTACT_BASE_URI + "/users/{openId}?user_id_type=open_id&department_id_type=open_department_id", openId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            Map<String, Object> user = child(child(requireSuccess(userResponse), "data"), "user");
            Object rawDepartmentIds = user.get("department_ids");
            if (!(rawDepartmentIds instanceof List<?> departmentIds)) {
                throw new FeishuDirectoryException(
                        "Feishu did not return department_ids; contact:user.department:readonly is required"
                );
            }

            List<FeishuDepartment> departments = new ArrayList<>();
            for (Object rawId : departmentIds) {
                String departmentId = String.valueOf(rawId);
                if (departmentId.isBlank() || "0".equals(departmentId)) {
                    continue;
                }
                try {
                    departments.add(loadDepartment(token, departmentId));
                } catch (RestClientException | FeishuDirectoryException ex) {
                    // Department IDs are enough to enforce visibility. Keep syncing with a stable
                    // placeholder until the application's contact data range includes this dept.
                    log.warn("Feishu department name is unavailable for {}: {}", departmentId, ex.getMessage());
                    departments.add(new FeishuDepartment(
                            departmentId,
                            "飞书部门 · " + departmentId.substring(Math.max(0, departmentId.length() - 6))
                    ));
                }
            }
            return Optional.of(List.copyOf(departments));
        } catch (RestClientException | FeishuDirectoryException ex) {
            log.warn("Skipping Feishu department sync for openId={}: {}", openId, ex.getMessage());
            return Optional.empty();
        }
    }

    public static List<Map<String, String>> toAttributeValue(List<FeishuDepartment> departments) {
        return departments.stream()
                .map(department -> {
                    Map<String, String> value = new LinkedHashMap<>();
                    value.put("id", department.externalId());
                    value.put("name", department.name());
                    return value;
                })
                .toList();
    }

    private FeishuDepartment loadDepartment(String token, String departmentId) {
        Map<String, Object> response = restClient.get()
                .uri(CONTACT_BASE_URI + "/departments/{departmentId}?department_id_type=open_department_id", departmentId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        Map<String, Object> department = child(child(requireSuccess(response), "data"), "department");
        String name = stringValue(department.get("name"));
        if (name == null) {
            throw new FeishuDirectoryException("Feishu department name is missing for " + departmentId);
        }
        String externalId = Optional.ofNullable(stringValue(department.get("open_department_id")))
                .orElse(departmentId);
        return new FeishuDepartment(externalId, name);
    }

    private String tenantAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
                return current.value();
            }
            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URI)
                    .body(Map.of("app_id", clientId, "app_secret", clientSecret))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            Map<String, Object> successful = requireSuccess(response);
            String value = stringValue(successful.get("tenant_access_token"));
            if (value == null) {
                throw new FeishuDirectoryException("Feishu tenant token is missing");
            }
            long expiresIn = successful.get("expire") instanceof Number number
                    ? number.longValue()
                    : 7_200L;
            cachedToken = new CachedToken(value, clock.instant().plusSeconds(expiresIn));
            return value;
        }
    }

    private static Map<String, Object> requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw new FeishuDirectoryException("Feishu response is empty");
        }
        int code = response.get("code") instanceof Number number ? number.intValue() : 0;
        if (code != 0) {
            throw new FeishuDirectoryException(
                    "Feishu API rejected the request (code=" + code + "): " + response.getOrDefault("msg", "unknown error")
            );
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new FeishuDirectoryException("Feishu response is missing " + key);
    }

    private static String stringValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    public record FeishuDepartment(String externalId, String name) { }

    private record CachedToken(String value, Instant expiresAt) { }

    private static final class FeishuDirectoryException extends RuntimeException {
        private FeishuDirectoryException(String message) {
            super(message);
        }
    }
}
