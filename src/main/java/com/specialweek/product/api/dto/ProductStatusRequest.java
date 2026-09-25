package com.specialweek.product.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull Integer status) {
}
