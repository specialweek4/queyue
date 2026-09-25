package com.specialweek.voucher.api.dto;

import jakarta.validation.constraints.NotNull;

public record VoucherStatusRequest(@NotNull Integer status) {
}
