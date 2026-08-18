package com.specialweek.storage.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确认提交响应
 */
@Data
public class ConfirmResponse {

    private List<ConfirmedObject> images = new ArrayList<>();

    private ConfirmedObject cover;

    private ConfirmedObject content;
}
