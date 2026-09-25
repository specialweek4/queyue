package com.specialweek.product.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductDetailRow {
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private String images;
    private Long price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String shopName;
    private String shopImages;
    private String shopAddress;
    private String shopOpenHours;
}
