package com.specialweek.blog.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BlogDetailRow {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String images;
    private String coverUrl;
    private String contentObjectKey;
    private Integer comments;
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String name;
    private String icon;
}
