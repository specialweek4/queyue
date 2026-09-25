package com.specialweek.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String nickName;
    private String avatar;
    private Integer role;
    private String email;
    private String bio;
    private String qyId;
    private String gender;
    private LocalDate birthday;
    private String school;
    private String tagsJson;
    private Integer fans;
    private Integer followee;
}
