package com.specialweek.product.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductFeedRow {
    private Long id;
    private Long shopId;
    private Long createdBy;
    private String name;
    private String description;
    private String images;
    private Long price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String creatorName;
    private String creatorIcon;
    private String shopName;
    private Long favorites;
}
