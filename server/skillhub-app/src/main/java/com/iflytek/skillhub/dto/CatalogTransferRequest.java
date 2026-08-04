package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record CatalogTransferRequest(@NotBlank String newOwnerId) {
}
