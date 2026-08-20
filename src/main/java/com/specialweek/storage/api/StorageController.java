package com.specialweek.storage.api;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.storage.config.OssProperties;
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
    @Resource
    private OssProperties ossProperties;

    @PostMapping("/presign")
    public Result presign(@RequestBody PresignRequest request, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());

        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }
        Blog post = blogService.getById(postId);
        if (post == null || !Long.valueOf(userId).equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        String scene = request.getScene();
        String ext = normalizeExt(request.getExt(), request.getContentType(), scene);
        String objectKey;
        switch (scene) {
            case "blog_content":
                objectKey = "unconfirmed/" + postId + "/content" + ext;
                break;
            case "blog_cover":
                objectKey = "unconfirmed/" + postId + "/cover" + ext;
                break;
            case "blog_image":
            default:
                String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
                objectKey = "unconfirmed/" + postId + "/images/" + date + "/" + UUID.randomUUID() + ext;
                break;
        }
        int expiresIn = 600;
        String putUrl = ossStorageService.generatePresignedPutUrl(objectKey, request.getContentType(), expiresIn);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        return Result.ok(new PresignResponse(objectKey, putUrl, headers, expiresIn));
    }

    @PostMapping("/confirm")
    public Result confirm(@RequestBody ConfirmRequest request, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }
        Blog post = blogService.getById(postId);
        if (post == null || !Long.valueOf(userId).equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        ConfirmResponse resp = new ConfirmResponse();
        List<ConfirmedObject> images = new ArrayList<>();
        if (request.getImageKeys() != null) {
            for (String key : request.getImageKeys()) {
                if (StrUtil.isBlank(key)) {
                    continue;
                }
                ConfirmedObject co = confirmObject(postId, key, "images/");
                if (co == null) {
                    return Result.fail("imageKey 非法: " + key);
                }
                images.add(co);
            }
        }
        resp.setImages(images);

        if (StrUtil.isNotBlank(request.getCoverKey())) {
            ConfirmedObject co = confirmObject(postId, request.getCoverKey(), "cover");
            if (co == null) {
                return Result.fail("coverKey 非法");
            }
            resp.setCover(co);
        }

        if (StrUtil.isNotBlank(request.getContentKey())) {
            ConfirmedObject co = confirmObject(postId, request.getContentKey(), "content.");
            if (co == null) {
                return Result.fail("contentKey 非法");
            }
            resp.setContent(co);
        }
        return Result.ok(resp);
    }

    private ConfirmedObject confirmObject(long postId, String key, String segment) {
        if (key.startsWith("blogs/" + postId + "/" + segment)) {
            return new ConfirmedObject(key, ossStorageService.publicUrl(key));
        }
        if (key.startsWith("unconfirmed/" + postId + "/" + segment)) {
            String target = "blogs/" + key.substring("unconfirmed/".length());
            ossStorageService.copyObject(key, target);
            return new ConfirmedObject(target, ossStorageService.publicUrl(target));
        }
        return null;
    }

    private String normalizeExt(String ext, String contentType, String scene) {
        if (StrUtil.isNotBlank(ext)) {
            return ext.startsWith(".") ? ext : "." + ext;
        }
        if ("blog_content".equals(scene)) {
            switch (contentType) {
                case "text/markdown": return ".md";
                case "text/html":     return ".html";
                case "text/plain":    return ".txt";
                default:              return ".bin";
            }
        }
        switch (contentType) {
            case "image/jpeg": return ".jpg";
            case "image/png":  return ".png";
            case "image/webp": return ".webp";
            default:           return ".img";
        }
    }
}
