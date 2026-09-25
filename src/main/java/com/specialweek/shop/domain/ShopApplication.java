package com.specialweek.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_application")
public class ShopApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer applyType;

    private Long applicantUserId;

    private Long targetShopId;

    private String name;

    private Long typeId;

    private String images;

    private String area;

    private String address;

    private BigDecimal x;

    private BigDecimal y;

    private Long avgPrice;

    private String openHours;

    private Integer auditStatus;

    private String rejectReason;

    private Long auditedBy;

    private LocalDateTime auditedTime;

    private Long approvedShopId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
