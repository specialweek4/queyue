package com.specialweek.auth.api.dto;

public record ResetPasswordRequest(String phone, String code, String newPassword) {
}
