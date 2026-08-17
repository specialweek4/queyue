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

    /** 非表字段：作者信息/点赞态/正文临时文本/正文访问地址 */
    @TableField(exist = false) private String icon;
    @TableField(exist = false) private String name;
    @TableField(exist = false) private Boolean isLike;
    /** 发布时前端随请求带上正文纯文本，仅用于后端自动生成摘要，不落库 */
    @TableField(exist = false) private String contentText;
    /** 详情接口返回：拼好的正文访问 URL，前端直接 fetch */
    @TableField(exist = false) private String contentUrl;
    @TableField(exist = false) private List<UserDTO> likes;     // 点赞用户列表（游客也有，属于公开信息）
    @TableField(exist = false) private Boolean followed;        // 是否已关注作者（仅登录返回）

    private String title;
    /** 摘要/简述，最多50字（热门笔记卡片展示） */
    private String description;
    /** 图片 URL 列表，逗号分隔 */
    private String images;
    /** 封面 URL */
    private String coverUrl;
    /** 正文 OSS 对象键（真实正文不在库里） */
    private String contentObjectKey;

    private Integer liked;
    private Integer comments;
    /** 0=草稿 1=已发布 */
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
