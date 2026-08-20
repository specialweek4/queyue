package com.specialweek.auth.api.dto;

public record LoginRequest(String phone, String code, String password) {
}
