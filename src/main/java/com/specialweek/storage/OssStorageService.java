package com.specialweek.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.specialweek.storage.config.OssProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;

@Service
public class OssStorageService {

    @Resource
    private OssProperties props;

    private OSS newClient() {
        return new OSSClientBuilder().build(
                props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
    }

    /**
     * 生成预签名
     * @param objectKey
     * @param contentType
     * @param expiresInSeconds
     * @return
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expiresInSeconds) {
        OSS client = newClient();
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(props.getBucketName(), objectKey, HttpMethod.PUT);
            request.setExpiration(new Date(System.currentTimeMillis() + expiresInSeconds * 1000L));
            if (StrUtil.isNotBlank(contentType)) {
                request.setContentType(contentType);
            }
            URL url = client.generatePresignedUrl(request);
            return url.toString();
        } finally {
            client.shutdown();
        }
    }

    /**
     * objectKey -> 公开访问 URL
     * @param objectKey
     * @return
     */
    public String publicUrl(String objectKey) {
        return StrUtil.removeSuffix(props.getPublicUrl(), "/") + "/" + objectKey;
    }

    /**
     * 同桶复制对象（确认提交：unconfirmed/ 临时区 -> blogs/ 正式区，服务端复制不经过后端流量）
     * @param sourceKey
     * @param targetKey
     */
    public void copyObject(String sourceKey, String targetKey) {
        OSS client = newClient();
        try {
            client.copyObject(props.getBucketName(), sourceKey, props.getBucketName(), targetKey);
        } finally {
            client.shutdown();
        }
    }

    /**
     * 删除对象（仅用于确认提交搬移后清理临时源；删除失败由 unconfirmed/ 生命周期规则兜底）
     * @param objectKey
     */
    public void deleteObject(String objectKey) {
        OSS client = newClient();
        try {
            client.deleteObject(props.getBucketName(), objectKey);
        } finally {
            client.shutdown();
        }
    }
}
