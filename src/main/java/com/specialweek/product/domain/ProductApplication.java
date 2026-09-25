package com.specialweek.product.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product_application")
public class ProductApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long applicantUserId;

    private Long shopId;

    private String name;

    private String description;

    private String images;

    private Long price;

    private Integer stock;

    private Integer auditStatus;

    private String rejectReason;

    private Long auditedBy;

    private LocalDateTime auditedTime;

    private Long approvedProductId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
