package com.specialweek.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.specialweek.storage.config.OssProperties;
import com.specialweek.storage.exception.StorageException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class OssStorageService {

    @Resource
    private OssProperties props;

    @Resource
    private OSS oss;

    /**
     * 上传用户头型
     * @param userId
     * @param file
     * @return
     */
    public String uploadAvatar(long userId, MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "";

        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }

        String objectKey = props.getFolder() + "/" + userId + "-" + Instant.now().toEpochMilli() + ext;

        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());

        try {
            PutObjectRequest request = new PutObjectRequest(props.getBucketName(), objectKey, file.getInputStream());
            client.putObject(request);
        } catch (IOException e) {
            throw new StorageException("头像读取失败");
        } finally {
            client.shutdown();
        }

        return publicUrl(objectKey);
    }

    /**
     * 生成 OSS PUT 预签名地址。
     */
    public String generatePresignedPutUrl(
            String objectKey,
            String contentType,
            int expiresInSeconds
    ) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(
                        props.getBucketName(),
                        objectKey,
                        HttpMethod.PUT
                );

        request.setExpiration(
                new Date(System.currentTimeMillis()
                        + expiresInSeconds * 1000L)
        );

        if (StrUtil.isNotBlank(contentType)) {
            request.setContentType(contentType);
        }

        return oss.generatePresignedUrl(request).toString();
    }

    /**
     * 根据 objectKey 生成正式访问 URL。
     *
     * 这个方法是后端内部使用的，不是前端输入 URL。
     */
    public String publicUrl(String objectKey) {
        return StrUtil.removeSuffix(props.getPublicUrl(), "/")
                + "/"
                + objectKey;
    }

    /**
     * OSS 服务端复制对象。
     */
    public void copyObject(String sourceKey, String targetKey) {
        oss.copyObject(
                props.getBucketName(),
                sourceKey,
                props.getBucketName(),
                targetKey
        );
    }

    /**
     * 商铺或商品申请提交时：
     * 有以下步骤
     * 1. 校验 objectKey 是否属于当前用户；
     * 2. 临时区对象复制到正式区；
     * 3. 正式区对象直接复用；
     * 4. 返回逗号分隔的正式图片 URL。
     *
     * 数据库 images 字段继续保存正式 URL。
     */
    public String promoteApplicationImages(
            List<String> imageKeys,
            long userId,
            String scene
    ) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return "";
        }

        if (!"shop".equals(scene) && !"product".equals(scene)) {
            throw new IllegalArgumentException("图片场景非法");
        }

        String temporaryPrefix =
                "unconfirmed/" + scene + "/" + userId + "/images/";

        String formalPrefix =
                ("shop".equals(scene) ? "shops/" : "products/")
                        + userId
                        + "/images/";

        List<String> formalUrls = new ArrayList<>();

        for (String rawKey : imageKeys) {
            if (StrUtil.isBlank(rawKey)) {
                continue;
            }

            String key = rawKey.trim();
            String formalKey;

            if (key.startsWith(temporaryPrefix)) {
                String suffix = key.substring(temporaryPrefix.length());

                validateSuffix(suffix);

                formalKey = formalPrefix + suffix;

                if (!oss.doesObjectExist(
                        props.getBucketName(),
                        key
                )) {
                    throw new IllegalArgumentException(
                            "图片尚未上传成功：" + key
                    );
                }

                copyObject(key, formalKey);

            } else if (key.startsWith(formalPrefix)) {
                /*
                 * 修改申请时，允许继续使用已经正式化的图片。
                 */
                validateSuffix(
                        key.substring(formalPrefix.length())
                );

                formalKey = key;

                if (!oss.doesObjectExist(
                        props.getBucketName(),
                        formalKey
                )) {
                    throw new IllegalArgumentException(
                            "正式图片不存在：" + formalKey
                    );
                }

            } else {
                throw new IllegalArgumentException(
                        "图片 objectKey 非法或不属于当前用户：" + key
                );
            }

            formalUrls.add(publicUrl(formalKey));
        }

        return String.join(",", formalUrls);
    }

    /**
     * 防止通过 objectKey 构造路径穿越或非法路径。
     */
    private void validateSuffix(String suffix) {
        if (StrUtil.isBlank(suffix)
                || suffix.contains("..")
                || suffix.contains("\\")
                || suffix.contains("?")
                || suffix.contains("#")
                || suffix.startsWith("/")
                || suffix.endsWith("/")) {
            throw new IllegalArgumentException("图片 objectKey 非法");
        }
    }

}
