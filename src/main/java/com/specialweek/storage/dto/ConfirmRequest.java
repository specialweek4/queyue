package com.specialweek.storage.dto;

import lombok.Data;

import java.util.List;

/**
 * 确认提交请求：把当前引用的临时区对象搬入正式区
 */
@Data
public class ConfirmRequest {

    private String postId;

    private List<String> imageKeys;

    private String coverKey;

    private String contentKey;
}
