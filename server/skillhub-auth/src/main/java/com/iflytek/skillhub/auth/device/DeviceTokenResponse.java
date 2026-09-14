package com.iflytek.skillhub.auth.device;

public record DeviceTokenResponse(
    String accessToken,
    String tokenType,
    String error
) {
    public static DeviceTokenResponse pending() {
        return new DeviceTokenResponse(null, null, "authorization_pending");
    }

    public static DeviceTokenResponse denied() {
        return new DeviceTokenResponse(null, null, "access_denied");
    }

    public static DeviceTokenResponse success(String token) {
        return new DeviceTokenResponse(token, "Bearer", null);
    }
}
