package com.specialweek.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认提交后的正式区对象引用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmedObject {
    /** 正式区对象 key（blogs/...） */
    private String objectKey;
    /** 公开访问 URL */
    private String url;
}
