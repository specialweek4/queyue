package com.specialweek.product.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductAuditRequest(
        @NotNull Boolean approved,
        @Size(max = 512) String reason
) {
}
