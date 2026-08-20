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

    private String objectKey;

    private String url;
}
