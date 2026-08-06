package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Size;

public record CatalogPublishRequest(@Size(max = 64) String version) {
}
