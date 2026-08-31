package com.specialweek.blog.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogFeedRow {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String images;
    private String coverUrl;
    private Integer comments;
    private LocalDateTime publishTime;
    private String name;
    private String icon;
}
