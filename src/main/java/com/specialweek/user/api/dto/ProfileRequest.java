package com.specialweek.user.api.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileRequest {
    private Long id;
    private String nickName;
    private String bio;
    private String gender;
    private LocalDate birthday;
    private String school;
    private String tagsJson;
}
