package com.specialweek.blog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.specialweek.user.api.dto.UserDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;

    @TableField(exist = false) private String icon;
    @TableField(exist = false) private String name;
    @TableField(exist = false) private Boolean isLike;
    @TableField(exist = false) private String contentText;
    @TableField(exist = false) private String contentUrl;
    @TableField(exist = false) private List<UserDTO> likes;     // 点赞用户列表（游客也有，属于公开信息）
    @TableField(exist = false) private Boolean followed;        // 是否已关注作者（仅登录返回）

    private String title;
    private String description;
    private String images;
    private String coverUrl;
    private String contentObjectKey;
    private Integer liked;
    private Integer comments;
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
