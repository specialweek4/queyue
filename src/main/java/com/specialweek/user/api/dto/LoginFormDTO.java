package com.specialweek.user.api.dto;

import lombok.Data;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
