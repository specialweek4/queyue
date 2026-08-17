package com.specialweek.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignResponse {
    private String objectKey;
    private String putUrl;
    private Map<String, String> headers;
    private int expiresIn;
}
