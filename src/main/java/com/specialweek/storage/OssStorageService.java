package com.specialweek.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.specialweek.storage.config.OssProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class OssStorageService {

    @Resource
    private OssProperties props;
    @Resource
    private OSS oss;

    /**
     * 生成预签名
     * @param objectKey
     * @param contentType
     * @param expiresInSeconds
     * @return
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expiresInSeconds) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(props.getBucketName(), objectKey, HttpMethod.PUT);
        request.setExpiration(new Date(System.currentTimeMillis() + expiresInSeconds * 1000L));
        if (StrUtil.isNotBlank(contentType)) {
            request.setContentType(contentType);
        }
        return oss.generatePresignedUrl(request).toString();
    }

    /**
     * objectKey变成公开访问 URL
     * @param objectKey
     * @return
     */
    public String publicUrl(String objectKey) {
        return StrUtil.removeSuffix(props.getPublicUrl(), "/") + "/" + objectKey;
    }

    /**
     * 确认提交：unconfirmed/ 临时区 -> blogs/ 正式区，服务端复制不经过后端流量
     * @param sourceKey
     * @param targetKey
     */
    public void copyObject(String sourceKey, String targetKey) {
        oss.copyObject(props.getBucketName(), sourceKey, props.getBucketName(), targetKey);
    }

    /**
     * 删除对象（仅用于确认提交搬移后清理临时源；删除失败由 unconfirmed/ 生命周期规则兜底）
     * @param objectKey
     */
    public void deleteObject(String objectKey) {
        oss.deleteObject(props.getBucketName(), objectKey);
    }
}
