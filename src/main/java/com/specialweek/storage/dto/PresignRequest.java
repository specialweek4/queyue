package com.specialweek.storage.dto;

import lombok.Data;

/**
 * 预签名直传请求
 */
@Data
public class PresignRequest {
    private String scene;
    //博客id
    private String postId;
    //标示是图片还是文章啥的
    private String contentType;
    //拓展名
    private String ext;
}
