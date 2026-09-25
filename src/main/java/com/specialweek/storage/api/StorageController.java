package com.specialweek.storage.api;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.storage.dto.ConfirmRequest;
import com.specialweek.storage.dto.ConfirmResponse;
import com.specialweek.storage.dto.ConfirmedObject;
import com.specialweek.storage.dto.PresignRequest;
import com.specialweek.storage.dto.PresignResponse;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageController {

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private IBlogService blogService;

    /**
     * 生成博客图片、封面、正文预签名。
     */
    @PostMapping("/presign")
    public Result presign(
            @RequestBody PresignRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = Long.parseLong(jwt.getSubject());

        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }

        Blog post = blogService.getById(postId);
        if (post == null
                || !Long.valueOf(userId).equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        String scene = request.getScene();
        String ext = normalizeExt(
                request.getExt(),
                request.getContentType(),
                scene
        );

        String objectKey;

        if ("blog_content".equals(scene)) {
            objectKey = "unconfirmed/"
                    + postId
                    + "/content"
                    + ext;

        } else if ("blog_cover".equals(scene)) {
            objectKey = "unconfirmed/"
                    + postId
                    + "/cover"
                    + ext;

        } else {
            String date = DateTimeFormatter
                    .ofPattern("yyyyMMdd")
                    .format(LocalDate.now());

            objectKey = "unconfirmed/"
                    + postId
                    + "/images/"
                    + date
                    + "/"
                    + UUID.randomUUID()
                    + ext;
        }

        int expiresIn = 600;

        String putUrl =
                ossStorageService.generatePresignedPutUrl(
                        objectKey,
                        request.getContentType(),
                        expiresIn
                );

        Map<String, String> headers = new HashMap<>();
        if (StrUtil.isNotBlank(request.getContentType())) {
            headers.put("Content-Type", request.getContentType());
        }

        return Result.ok(
                new PresignResponse(
                        objectKey,
                        putUrl,
                        headers,
                        expiresIn
                )
        );
    }

    /**
     * 博客提交确认接口。
     */
    @PostMapping("/confirm")
    public Result confirm(
            @RequestBody ConfirmRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = Long.parseLong(jwt.getSubject());

        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }

        Blog post = blogService.getById(postId);
        if (post == null
                || !Long.valueOf(userId).equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        ConfirmResponse response = new ConfirmResponse();

        List<ConfirmedObject> images = new ArrayList<>();

        if (request.getImageKeys() != null) {
            for (String key : request.getImageKeys()) {
                if (StrUtil.isBlank(key)) {
                    continue;
                }

                ConfirmedObject confirmed =
                        confirmBlogObject(postId, key, "images/");

                if (confirmed == null) {
                    return Result.fail("imageKey 非法");
                }

                images.add(confirmed);
            }
        }

        response.setImages(images);

        if (StrUtil.isNotBlank(request.getCoverKey())) {
            ConfirmedObject confirmed =
                    confirmBlogObject(
                            postId,
                            request.getCoverKey(),
                            "cover"
                    );

            if (confirmed == null) {
                return Result.fail("coverKey 非法");
            }

            response.setCover(confirmed);
        }

        if (StrUtil.isNotBlank(request.getContentKey())) {
            ConfirmedObject confirmed =
                    confirmBlogObject(
                            postId,
                            request.getContentKey(),
                            "content."
                    );

            if (confirmed == null) {
                return Result.fail("contentKey 非法");
            }

            response.setContent(confirmed);
        }

        return Result.ok(response);
    }

    /**
     * 商铺图片预签名。
     */
    @PostMapping("/presign/shop-image")
    public Result presignShopImage(
            @RequestBody PresignRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return Result.ok(
                createMerchantImagePresign(
                        request,
                        userId,
                        "shop"
                )
        );
    }

    /**
     * 商品图片预签名。
     */
    @PostMapping("/presign/product-image")
    public Result presignProductImage(
            @RequestBody PresignRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return Result.ok(
                createMerchantImagePresign(
                        request,
                        userId,
                        "product"
                )
        );
    }

    /**
     * 商铺和商品共用图片预签名逻辑。
     */
    private PresignResponse createMerchantImagePresign(
            PresignRequest request,
            long userId,
            String scene
    ) {
        String ext = normalizeExt(
                request.getExt(),
                request.getContentType(),
                "image"
        );

        String date = DateTimeFormatter
                .ofPattern("yyyyMMdd")
                .format(LocalDate.now());

        String objectKey =
                "unconfirmed/"
                        + scene
                        + "/"
                        + userId
                        + "/images/"
                        + date
                        + "/"
                        + UUID.randomUUID()
                        + ext;

        int expiresIn = 600;

        String putUrl =
                ossStorageService.generatePresignedPutUrl(
                        objectKey,
                        request.getContentType(),
                        expiresIn
                );

        Map<String, String> headers = new HashMap<>();
        if (StrUtil.isNotBlank(request.getContentType())) {
            headers.put("Content-Type", request.getContentType());
        }

        return new PresignResponse(
                objectKey,
                putUrl,
                headers,
                expiresIn
        );
    }

    /**
     * 博客临时对象复制到正式区。
     */
    private ConfirmedObject confirmBlogObject(
            long postId,
            String key,
            String segment
    ) {
        String formalPrefix =
                "blogs/" + postId + "/" + segment;

        String temporaryPrefix =
                "unconfirmed/" + postId + "/" + segment;

        if (key.startsWith(formalPrefix)) {
            return new ConfirmedObject(
                    key,
                    ossStorageService.publicUrl(key)
            );
        }

        if (key.startsWith(temporaryPrefix)) {
            String target =
                    "blogs/" + key.substring("unconfirmed/".length());

            ossStorageService.copyObject(key, target);

            return new ConfirmedObject(
                    target,
                    ossStorageService.publicUrl(target)
            );
        }

        return null;
    }

    private String normalizeExt(
            String ext,
            String contentType,
            String scene
    ) {
        if (StrUtil.isNotBlank(ext)) {
            return ext.startsWith(".") ? ext : "." + ext;
        }

        String type = StrUtil.isBlank(contentType)
                ? ""
                : contentType;

        if ("blog_content".equals(scene)) {
            return switch (type) {
                case "text/markdown" -> ".md";
                case "text/html" -> ".html";
                case "text/plain" -> ".txt";
                default -> ".bin";
            };
        }

        return switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }
}
